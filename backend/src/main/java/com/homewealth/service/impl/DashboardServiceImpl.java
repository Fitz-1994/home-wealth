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

        // 分类标签
        Map<String, String> categoryLabels = Map.of(
                "LIQUID", "流动资金", "FIXED", "固定资产",
                "RECEIVABLE", "应收款", "INVESTMENT", "投资理财", "LIABILITY", "负债"
        );

        nodes.add(new SankeyDataVO.Node("总资产"));
        nodes.add(new SankeyDataVO.Node("负债"));

        Map<String, BigDecimal> categoryTotals = new LinkedHashMap<>();
        Map<String, List<Object[]>> accountDetails = new LinkedHashMap<>(); // category -> [[name, cny]]

        List<AssetAccount> accounts = accountMapper.findByUserId(userId, null, null);

        for (AssetAccount account : accounts) {
            BigDecimal cny = getAccountValueCny(account, userId);
            if (cny.compareTo(BigDecimal.ZERO) == 0) continue;

            String cat = account.getAssetCategory();
            categoryTotals.merge(cat, cny, BigDecimal::add);
            accountDetails.computeIfAbsent(cat, k -> new ArrayList<>())
                    .add(new Object[]{account.getAccountName(), cny});
        }

        // 计算总资产（不含负债）
        BigDecimal totalAsset = categoryTotals.entrySet().stream()
                .filter(e -> !"LIABILITY".equals(e.getKey()))
                .map(Map.Entry::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 资产大类 -> 总资产
        for (Map.Entry<String, BigDecimal> entry : categoryTotals.entrySet()) {
            if ("LIABILITY".equals(entry.getKey())) continue;
            String label = categoryLabels.getOrDefault(entry.getKey(), entry.getKey());
            nodes.add(new SankeyDataVO.Node(label));
            links.add(new SankeyDataVO.Link("总资产", label, entry.getValue()));
        }

        // 负债
        if (categoryTotals.containsKey("LIABILITY")) {
            BigDecimal liabilityVal = categoryTotals.get("LIABILITY");
            links.add(new SankeyDataVO.Link("负债", "总资产", liabilityVal)); // 负债指向总资产（方向表意）
        }

        // 账户 -> 大类（小于总资产0.5%的合并为"其他"）
        BigDecimal threshold = totalAsset.multiply(new BigDecimal("0.005"));
        for (Map.Entry<String, List<Object[]>> entry : accountDetails.entrySet()) {
            if ("LIABILITY".equals(entry.getKey())) continue;
            String catLabel = categoryLabels.getOrDefault(entry.getKey(), entry.getKey());
            BigDecimal otherCny = BigDecimal.ZERO;

            for (Object[] item : entry.getValue()) {
                String name = (String) item[0];
                BigDecimal cny = (BigDecimal) item[1];
                if (cny.compareTo(threshold) >= 0) {
                    nodes.add(new SankeyDataVO.Node(name));
                    links.add(new SankeyDataVO.Link(catLabel, name, cny));
                } else {
                    otherCny = otherCny.add(cny);
                }
            }
            if (otherCny.compareTo(BigDecimal.ZERO) > 0) {
                String otherLabel = catLabel + "-其他";
                nodes.add(new SankeyDataVO.Node(otherLabel));
                links.add(new SankeyDataVO.Link(catLabel, otherLabel, otherCny));
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
                    .multiply(price.getPrice())
                    .multiply(BigDecimal.valueOf(holding.getLotSize()));
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
                        .multiply(price.getPrice())
                        .multiply(BigDecimal.valueOf(holding.getLotSize()));
                total = total.add(exchangeRateService.toCny(mv, price.getCurrency()));
            }
            return total;
        }
    }
}
