package com.homewealth.service.impl;

import com.homewealth.dto.response.*;
import com.homewealth.mapper.*;
import com.homewealth.model.*;
import com.homewealth.service.DashboardService;
import com.homewealth.service.ExchangeRateService;
import com.homewealth.service.MarketDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final AssetAccountMapper accountMapper;
    private final RegularAccountRecordMapper recordMapper;
    private final InvestmentHoldingMapper holdingMapper;
    private final MarketPriceCacheMapper priceCacheMapper;
    private final DailyNetAssetSnapshotMapper netSnapshotMapper;
    private final DailyInvestmentSnapshotMapper invSnapshotMapper;
    private final ExchangeRateService exchangeRateService;
    private final MarketDataService marketDataService;

    @Override
    public DashboardOverviewVO getOverview(Long userId) {
        Map<String, BigDecimal> categories = new LinkedHashMap<>();
        categories.put("LIQUID", BigDecimal.ZERO);
        categories.put("FIXED", BigDecimal.ZERO);
        categories.put("RECEIVABLE", BigDecimal.ZERO);
        categories.put("INVESTMENT", BigDecimal.ZERO);
        categories.put("LIABILITY", BigDecimal.ZERO);

        List<AssetAccount> accounts = accountMapper.findByUserId(userId, null, null);

        for (AssetAccount account : accounts) {
            BigDecimal cny = getAccountValueCny(account, userId);
            // 负债账户取绝对值，统一用正数表示负债规模
            if ("LIABILITY".equals(account.getAssetCategory())) {
                cny = cny.abs();
            }
            categories.merge(account.getAssetCategory(), cny, BigDecimal::add);
        }

        BigDecimal totalAsset = categories.get("LIQUID")
                .add(categories.get("FIXED"))
                .add(categories.get("RECEIVABLE"))
                .add(categories.get("INVESTMENT"));
        BigDecimal liability = categories.get("LIABILITY");
        BigDecimal netAsset = totalAsset.subtract(liability);

        DashboardOverviewVO vo = new DashboardOverviewVO();
        vo.setTotalAssetCny(totalAsset);
        vo.setTotalLiabilityCny(liability);
        vo.setNetAssetCny(netAsset);
        vo.setCategories(categories);
        vo.setInvestmentMarketValue(categories.get("INVESTMENT"));
        vo.setLastUpdated(LocalDateTime.now());
        return vo;
    }

    @Override
    public SankeyDataVO getSankeyData(Long userId) {
        SankeyDataVO vo = new SankeyDataVO();
        List<SankeyDataVO.Node> nodes = new ArrayList<>();
        List<SankeyDataVO.Link> links = new ArrayList<>();

        Map<String, String> categoryLabels = Map.of(
                "LIQUID", "流动资金", "FIXED", "固定资产",
                "RECEIVABLE", "应收款", "INVESTMENT", "投资理财"
        );

        // 按资产总额降序排列大类，避免大类之间的连线交叉
        Map<String, BigDecimal> categoryTotals = new LinkedHashMap<>();
        // category -> [[name, cny]]，子账户按金额降序，避免叶节点连线交叉
        Map<String, List<Object[]>> accountDetails = new LinkedHashMap<>();
        List<Object[]> liabilityAccounts = new ArrayList<>();

        List<AssetAccount> accounts = accountMapper.findByUserId(userId, null, null);
        for (AssetAccount account : accounts) {
            BigDecimal cny = getAccountValueCny(account, userId);
            if (cny.compareTo(BigDecimal.ZERO) == 0) continue;

            String cat = account.getAssetCategory();
            if ("LIABILITY".equals(cat)) {
                // 负债金额取绝对值：数据库可能存正数或负数，统一用正数表示负债规模
                liabilityAccounts.add(new Object[]{account.getAccountName(), cny.abs()});
            } else {
                categoryTotals.merge(cat, cny, BigDecimal::add);
                accountDetails.computeIfAbsent(cat, k -> new ArrayList<>())
                        .add(new Object[]{account.getAccountName(), cny});
            }
        }

        // 大类按金额降序排列，使最大流在顶部，避免交叉
        List<Map.Entry<String, BigDecimal>> sortedCategories = categoryTotals.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .collect(Collectors.toList());

        BigDecimal totalAsset = categoryTotals.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal liability = liabilityAccounts.stream()
                .map(a -> (BigDecimal) a[1]).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal netAsset = totalAsset.subtract(liability);

        boolean hasLiability = liability.compareTo(BigDecimal.ZERO) > 0;

        // 深度定义：总资产始终在 depth=2（视觉中轴），左侧最多占 depth=0,1，右侧占 depth=3,4
        // 无负债时净资产在 depth=0，depth=1 空置，总资产在 depth=2（50%宽度处）
        int depthNetAsset    = 0;
        int depthLiability   = 1;           // 仅在 hasLiability 时使用
        int depthTotalAsset  = 2;
        int depthCategory    = 3;
        int depthAccount     = 4;

        // ── 节点（顺序决定同列内的上下位置，从上到下）──

        // depth 0: 净资产在上，负债账户在下
        nodes.add(new SankeyDataVO.Node("净资产", depthNetAsset));
        if (hasLiability) {
            // 负债账户按金额降序排列
            liabilityAccounts.sort((a, b) -> ((BigDecimal) b[1]).compareTo((BigDecimal) a[1]));
            for (Object[] la : liabilityAccounts) {
                nodes.add(new SankeyDataVO.Node((String) la[0], depthNetAsset));
            }
            nodes.add(new SankeyDataVO.Node("负债", depthLiability));
        }

        // 总资产
        nodes.add(new SankeyDataVO.Node("总资产", depthTotalAsset));

        // 资产大类（按金额降序）
        for (Map.Entry<String, BigDecimal> entry : sortedCategories) {
            nodes.add(new SankeyDataVO.Node(categoryLabels.getOrDefault(entry.getKey(), entry.getKey()), depthCategory));
        }

        // 资产账户：按大类顺序分组，组内按金额降序，消除交叉
        BigDecimal threshold = totalAsset.multiply(new BigDecimal("0.005"));
        for (Map.Entry<String, BigDecimal> catEntry : sortedCategories) {
            String cat = catEntry.getKey();
            String catLabel = categoryLabels.getOrDefault(cat, cat);
            List<Object[]> accts = accountDetails.getOrDefault(cat, Collections.emptyList());
            accts.sort((a, b) -> ((BigDecimal) b[1]).compareTo((BigDecimal) a[1]));

            BigDecimal otherCny = BigDecimal.ZERO;
            for (Object[] item : accts) {
                BigDecimal cny = (BigDecimal) item[1];
                if (cny.compareTo(threshold) >= 0) {
                    nodes.add(new SankeyDataVO.Node((String) item[0], depthAccount));
                } else {
                    otherCny = otherCny.add(cny);
                }
            }
            if (otherCny.compareTo(BigDecimal.ZERO) > 0) {
                nodes.add(new SankeyDataVO.Node(catLabel + "-其他", depthAccount));
            }
        }

        // ── 连线 ──

        links.add(new SankeyDataVO.Link("净资产", "总资产", netAsset));

        if (hasLiability) {
            for (Object[] la : liabilityAccounts) {
                links.add(new SankeyDataVO.Link((String) la[0], "负债", (BigDecimal) la[1]));
            }
            links.add(new SankeyDataVO.Link("负债", "总资产", liability));
        }

        for (Map.Entry<String, BigDecimal> entry : sortedCategories) {
            String catLabel = categoryLabels.getOrDefault(entry.getKey(), entry.getKey());
            links.add(new SankeyDataVO.Link("总资产", catLabel, entry.getValue()));
        }

        for (Map.Entry<String, BigDecimal> catEntry : sortedCategories) {
            String cat = catEntry.getKey();
            String catLabel = categoryLabels.getOrDefault(cat, cat);
            List<Object[]> accts = accountDetails.getOrDefault(cat, Collections.emptyList());
            accts.sort((a, b) -> ((BigDecimal) b[1]).compareTo((BigDecimal) a[1]));

            BigDecimal otherCny = BigDecimal.ZERO;
            for (Object[] item : accts) {
                String name = (String) item[0];
                BigDecimal cny = (BigDecimal) item[1];
                if (cny.compareTo(threshold) >= 0) {
                    links.add(new SankeyDataVO.Link(catLabel, name, cny));
                } else {
                    otherCny = otherCny.add(cny);
                }
            }
            if (otherCny.compareTo(BigDecimal.ZERO) > 0) {
                links.add(new SankeyDataVO.Link(catLabel, catLabel + "-其他", otherCny));
            }
        }

        vo.setNodes(nodes);
        vo.setLinks(links);
        return vo;
    }

    @Override
    public ChartDataVO getNetAssetHistory(Long userId, int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = days <= 0 ? null : endDate.minusDays(days);

        List<DailyNetAssetSnapshot> snapshots = netSnapshotMapper.findByUserId(userId, startDate, endDate);

        List<LocalDate> dates = snapshots.stream().map(DailyNetAssetSnapshot::getSnapshotDate).toList();
        List<BigDecimal> values = snapshots.stream().map(DailyNetAssetSnapshot::getNetAssetCny).toList();
        return ChartDataVO.from(dates, values);
    }

    @Override
    public ChartDataVO getInvestmentHistory(Long userId, int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = days <= 0 ? null : endDate.minusDays(days);

        List<DailyInvestmentSnapshot> snapshots = invSnapshotMapper.findByUserId(userId, startDate, endDate);

        List<LocalDate> dates = snapshots.stream().map(DailyInvestmentSnapshot::getSnapshotDate).toList();
        List<BigDecimal> values = snapshots.stream().map(DailyInvestmentSnapshot::getTotalValueCny).toList();
        return ChartDataVO.from(dates, values);
    }

    @Override
    public HoldingRankVO getHoldingRank(Long userId, int top) {
        List<InvestmentHolding> holdings = holdingMapper.findActiveByUserId(userId);
        if (holdings.isEmpty()) {
            HoldingRankVO vo = new HoldingRankVO();
            vo.setItems(Collections.emptyList());
            vo.setTotalValueCny(BigDecimal.ZERO);
            return vo;
        }

        List<String> symbols = holdings.stream().map(InvestmentHolding::getSymbol).distinct().toList();
        Map<String, MarketPriceCache> priceMap = marketDataService.getLatestPrices(symbols);

        List<HoldingRankVO.HoldingRankItem> items = new ArrayList<>();
        BigDecimal totalCny = BigDecimal.ZERO;

        for (InvestmentHolding holding : holdings) {
            MarketPriceCache price = priceMap.get(holding.getSymbol());
            if (price == null) continue;

            BigDecimal mv = holding.getQuantity()
                    .multiply(price.getPrice());
            BigDecimal mvCny = exchangeRateService.toCny(mv, price.getCurrency());
            totalCny = totalCny.add(mvCny);

            HoldingRankVO.HoldingRankItem item = new HoldingRankVO.HoldingRankItem();
            item.setHoldingId(holding.getId());
            item.setSymbol(holding.getSymbol());
            // 优先使用行情缓存中的标的名称（来自 Yahoo Finance shortName）
            item.setSymbolName(price.getSymbolName() != null && !price.getSymbolName().isEmpty()
                    ? price.getSymbolName() : holding.getSymbolName());
            item.setMarket(holding.getMarket());
            item.setQuantity(holding.getQuantity());
            item.setCurrentPrice(price.getPrice());
            item.setPriceCurrency(price.getCurrency());
            item.setMarketValueCny(mvCny);
            item.setPriceChangePct(price.getChangePct());
            items.add(item);
        }

        // 按市值降序
        items.sort(Comparator.comparing(HoldingRankVO.HoldingRankItem::getMarketValueCny).reversed());

        // 计算占比
        BigDecimal total = totalCny;
        items.forEach(item -> {
            if (total.compareTo(BigDecimal.ZERO) > 0) {
                item.setRatio(item.getMarketValueCny().divide(total, 4, RoundingMode.HALF_UP));
            }
        });

        // 截取 top N
        if (top > 0 && items.size() > top) {
            items = items.subList(0, top);
        }

        HoldingRankVO vo = new HoldingRankVO();
        vo.setItems(items);
        vo.setTotalValueCny(totalCny);
        return vo;
    }

    @Override
    public Map<Long, BigDecimal> getAccountValues(Long userId) {
        List<AssetAccount> accounts = accountMapper.findByUserId(userId, null, null);
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        for (AssetAccount account : accounts) {
            BigDecimal cny = getAccountValueCny(account, userId);
            // 负债账户取绝对值
            if ("LIABILITY".equals(account.getAssetCategory())) {
                cny = cny.abs();
            }
            result.put(account.getId(), cny);
        }
        return result;
    }

    private BigDecimal getAccountValueCny(AssetAccount account, Long userId) {
        if ("REGULAR".equals(account.getAccountType())) {
            RegularAccountRecord record = recordMapper.findCurrentByAccountId(account.getId());
            return record != null ? record.getCnyAmount() : BigDecimal.ZERO;
        } else {
            // INVESTMENT
            List<InvestmentHolding> holdings = holdingMapper.findByUserId(userId, account.getId(), null);
            if (holdings.isEmpty()) return BigDecimal.ZERO;

            List<String> symbols = holdings.stream().map(InvestmentHolding::getSymbol).distinct().toList();
            Map<String, MarketPriceCache> priceMap = marketDataService.getLatestPrices(symbols);

            BigDecimal total = BigDecimal.ZERO;
            for (InvestmentHolding holding : holdings) {
                MarketPriceCache price = priceMap.get(holding.getSymbol());
                if (price == null) continue;
                BigDecimal mv = holding.getQuantity()
                        .multiply(price.getPrice());
                total = total.add(exchangeRateService.toCny(mv, price.getCurrency()));
            }
            return total;
        }
    }
}
