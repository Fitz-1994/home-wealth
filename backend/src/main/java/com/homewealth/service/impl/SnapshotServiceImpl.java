package com.homewealth.service.impl;

import com.homewealth.mapper.*;
import com.homewealth.market.ChinaFundFetcher;
import com.homewealth.market.DailyClose;
import com.homewealth.market.MarketHistoryFetcher;
import com.homewealth.model.*;
import com.homewealth.model.InvestmentCashBalance;
import com.homewealth.service.ExchangeRateService;
import com.homewealth.service.MarketDataService;
import com.homewealth.service.SnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotServiceImpl implements SnapshotService {

    private final DailyNetAssetSnapshotMapper netSnapshotMapper;
    private final DailyInvestmentSnapshotMapper invSnapshotMapper;
    private final AssetAccountMapper accountMapper;
    private final RegularAccountRecordMapper recordMapper;
    private final InvestmentHoldingMapper holdingMapper;
    private final MarketPriceCacheMapper priceCacheMapper;
    private final InvestmentCashBalanceMapper cashBalanceMapper;
    private final InvestmentTransactionMapper txnMapper;
    private final ExchangeRateService exchangeRateService;
    private final MarketDataService marketDataService;
    private final UserMapper userMapper;
    private final MarketHistoryFetcher historyFetcher;
    private final ChinaFundFetcher fundFetcher;

    @Override
    public void generateSnapshot(Long userId, LocalDate date) {
        generateSnapshot(userId, date, null);
    }

    /**
     * @param priceOverride 非空时按该价格表定价（用于按历史收盘价回补），
     *                      缺失的标的回退到 price_cache 中的最新价
     */
    private void generateSnapshot(Long userId, LocalDate date, Map<String, BigDecimal> priceOverride) {
        log.info("Generating snapshot for userId={} date={}", userId, date);

        // 获取该用户所有活跃账户
        List<AssetAccount> accounts = accountMapper.findByUserId(userId, null, null);

        // 分类汇总各类资产（CNY）
        BigDecimal liquid = BigDecimal.ZERO, fixed = BigDecimal.ZERO,
                receivable = BigDecimal.ZERO, investment = BigDecimal.ZERO, liability = BigDecimal.ZERO;

        // 投资分类汇总
        BigDecimal cnA = BigDecimal.ZERO, hk = BigDecimal.ZERO, us = BigDecimal.ZERO,
                hkOpt = BigDecimal.ZERO, usOpt = BigDecimal.ZERO, other = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;

        for (AssetAccount account : accounts) {
            if ("REGULAR".equals(account.getAccountType())) {
                RegularAccountRecord current = recordMapper.findCurrentByAccountId(account.getId());
                if (current == null) continue;
                BigDecimal cny = current.getCnyAmount() != null ? current.getCnyAmount() : BigDecimal.ZERO;

                switch (account.getAssetCategory()) {
                    case "LIQUID"     -> liquid     = liquid.add(cny);
                    case "FIXED"      -> fixed      = fixed.add(cny);
                    case "RECEIVABLE" -> receivable = receivable.add(cny);
                    case "INVESTMENT" -> investment = investment.add(cny);
                    case "LIABILITY"  -> liability  = liability.add(cny);
                }
            } else if ("INVESTMENT".equals(account.getAccountType())) {
                List<InvestmentHolding> holdings = holdingMapper.findByUserId(userId, account.getId(), null);
                List<String> symbols = holdings.stream().map(InvestmentHolding::getSymbol).distinct().collect(Collectors.toList());
                Map<String, MarketPriceCache> priceMap = marketDataService.getLatestPrices(symbols);

                for (InvestmentHolding holding : holdings) {
                    MarketPriceCache price = priceMap.get(holding.getSymbol());
                    if (price == null) continue;

                    // 回补时优先使用该日历史收盘价，缺失则沿用缓存中的最新价
                    BigDecimal unitPrice = price.getPrice();
                    if (priceOverride != null) {
                        BigDecimal historical = priceOverride.get(holding.getSymbol());
                        if (historical != null) unitPrice = historical;
                    }

                    BigDecimal mv = holding.getQuantity()
                            .multiply(unitPrice);
                    BigDecimal mvCny = exchangeRateService.toCny(mv, price.getCurrency());
                    investment = investment.add(mvCny);

                    switch (holding.getMarket()) {
                        case "CN_A"   -> cnA   = cnA.add(mvCny);
                        case "HK"     -> hk    = hk.add(mvCny);
                        case "US"     -> us    = us.add(mvCny);
                        case "HK_OPT" -> hkOpt = hkOpt.add(mvCny);
                        case "US_OPT" -> usOpt = usOpt.add(mvCny);
                        default       -> other = other.add(mvCny);
                    }

                    // 成本汇总
                    if (holding.getCostPrice() != null) {
                        BigDecimal cost = holding.getCostPrice()
                                .multiply(holding.getQuantity());
                        totalCost = totalCost.add(exchangeRateService.toCny(cost, holding.getPriceCurrency()));
                    }
                }

                // 投资账户现金余额
                List<InvestmentCashBalance> cashBalances = cashBalanceMapper.findByAccountId(account.getId());
                for (InvestmentCashBalance cash : cashBalances) {
                    BigDecimal cashCny = exchangeRateService.toCny(cash.getAmount(), cash.getCurrency());
                    investment = investment.add(cashCny);
                    other = other.add(cashCny);
                }
            }
        }

        BigDecimal totalAsset = liquid.add(fixed).add(receivable).add(investment);
        BigDecimal netAsset = totalAsset.subtract(liability);

        // 保存净资产快照
        DailyNetAssetSnapshot netSnapshot = new DailyNetAssetSnapshot();
        netSnapshot.setUserId(userId);
        netSnapshot.setSnapshotDate(date);
        netSnapshot.setTotalAssetCny(totalAsset);
        netSnapshot.setTotalLiabilityCny(liability);
        netSnapshot.setNetAssetCny(netAsset);
        netSnapshot.setLiquidCny(liquid);
        netSnapshot.setFixedCny(fixed);
        netSnapshot.setReceivableCny(receivable);
        netSnapshot.setInvestmentCny(investment);
        netSnapshot.setLiabilityCny(liability);
        netSnapshotMapper.upsert(netSnapshot);

        // 保存投资快照
        DailyInvestmentSnapshot invSnapshot = new DailyInvestmentSnapshot();
        invSnapshot.setUserId(userId);
        invSnapshot.setSnapshotDate(date);
        invSnapshot.setTotalValueCny(investment);
        invSnapshot.setTotalCostCny(totalCost.compareTo(BigDecimal.ZERO) > 0 ? totalCost : null);
        invSnapshot.setUnrealizedPnl(totalCost.compareTo(BigDecimal.ZERO) > 0 ? investment.subtract(totalCost) : null);
        invSnapshot.setCnAValueCny(cnA);
        invSnapshot.setHkValueCny(hk);
        invSnapshot.setUsValueCny(us);
        invSnapshot.setHkOptValueCny(hkOpt);
        invSnapshot.setUsOptValueCny(usOpt);
        invSnapshot.setOtherValueCny(other);

        // 当日净入金（仅 CASH_IN/CASH_OUT 进入此口径，DIVIDEND/FEE 不算入金）
        BigDecimal netCashflow = txnMapper.sumNetCashflowByDate(userId, date);
        invSnapshot.setNetCashflowCny(netCashflow != null ? netCashflow : BigDecimal.ZERO);

        invSnapshotMapper.upsert(invSnapshot);

        log.info("Snapshot done for userId={}: net={} investment={}", userId, netAsset, investment);
    }

    @Override
    public void generateSnapshotForAllUsers(LocalDate date) {
        List<Long> userIds = userMapper.findAllActiveUserIds();
        for (Long userId : userIds) {
            generateSnapshot(userId, date);
        }
    }

    @Override
    public void deleteSnapshot(Long userId, LocalDate date) {
        netSnapshotMapper.deleteByUserIdAndDate(userId, date);
    }

    @Override
    public BackfillResult backfillSnapshots(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new IllegalArgumentException("回补区间无效: " + from + " ~ " + to);
        }
        log.info("[Backfill] rebuilding snapshots {} ~ {}", from, to);

        List<String> symbols = holdingMapper.findAllActiveSymbols();
        // 多留一倍余量，覆盖区间内的非交易日
        int days = (int) (ChronoUnit.DAYS.between(from, to) + 1) * 2 + 10;

        // symbol → (日期 → 收盘价)
        Map<String, Map<LocalDate, BigDecimal>> historyBySymbol = new HashMap<>();
        List<String> carriedForward = new ArrayList<>();

        for (String symbol : symbols) {
            List<DailyClose> closes = symbol.matches("\\d{6}")
                    ? fundFetcher.fetchNavHistory(symbol, days)
                    : historyFetcher.fetchDailyCloses(symbol, days);

            if (closes.isEmpty()) {
                // 期权等无免费历史源的标的：沿用 price_cache 中的最新价
                carriedForward.add(symbol);
                continue;
            }
            Map<LocalDate, BigDecimal> byDate = new HashMap<>();
            for (DailyClose dc : closes) byDate.put(dc.getDate(), dc.getClose());
            historyBySymbol.put(symbol, byDate);
        }

        if (!carriedForward.isEmpty()) {
            log.warn("[Backfill] no historical prices for {} symbols, carrying forward latest cached price: {}",
                    carriedForward.size(), carriedForward);
        }

        List<Long> userIds = userMapper.findAllActiveUserIds();
        int rebuilt = 0;
        // 覆盖区间内每一天：调度器在周末/节假日同样会生成快照，
        // 这些日子按“之前最近一个交易日”的收盘价定价，与休市时的真实市值一致。
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            final LocalDate d = date;
            Map<String, BigDecimal> priceOnDate = new HashMap<>();
            historyBySymbol.forEach((symbol, byDate) ->
                    priceAsOf(byDate, d).ifPresent(p -> priceOnDate.put(symbol, p)));

            if (priceOnDate.isEmpty()) {
                log.warn("[Backfill] no priced symbols for {}, skipping", d);
                continue;
            }
            for (Long userId : userIds) {
                generateSnapshot(userId, d, priceOnDate);
            }
            rebuilt++;
        }

        log.info("[Backfill] done: {} dates rebuilt, {} symbols priced from history, {} carried forward",
                rebuilt, historyBySymbol.size(), carriedForward.size());
        return new BackfillResult(from, to, rebuilt, historyBySymbol.size(), carriedForward);
    }

    /** 取 date 当日收盘价；停牌无数据时回退到之前最近一个有价日 */
    private static Optional<BigDecimal> priceAsOf(Map<LocalDate, BigDecimal> byDate, LocalDate date) {
        BigDecimal exact = byDate.get(date);
        if (exact != null) return Optional.of(exact);
        return byDate.entrySet().stream()
                .filter(e -> !e.getKey().isAfter(date))
                .max(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue);
    }

    @Override
    public DailyNetAssetSnapshot getSnapshot(Long userId, LocalDate date) {
        return netSnapshotMapper.findByUserIdAndDate(userId, date);
    }
}
