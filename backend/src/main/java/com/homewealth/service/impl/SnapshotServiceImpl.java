package com.homewealth.service.impl;

import com.homewealth.mapper.*;
import com.homewealth.model.*;
import com.homewealth.service.ExchangeRateService;
import com.homewealth.service.MarketDataService;
import com.homewealth.service.SnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
    private final ExchangeRateService exchangeRateService;
    private final MarketDataService marketDataService;
    private final UserMapper userMapper;

    @Override
    public void generateSnapshot(Long userId, LocalDate date) {
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

                    BigDecimal mv = holding.getQuantity()
                            .multiply(price.getPrice());
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
    public DailyNetAssetSnapshot getSnapshot(Long userId, LocalDate date) {
        return netSnapshotMapper.findByUserIdAndDate(userId, date);
    }
}
