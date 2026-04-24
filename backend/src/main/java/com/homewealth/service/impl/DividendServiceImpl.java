package com.homewealth.service.impl;

import com.homewealth.dto.response.DividendDetailVO;
import com.homewealth.dto.response.DividendHistoryVO;
import com.homewealth.dto.response.DividendSummaryVO;
import com.homewealth.mapper.DividendEventMapper;
import com.homewealth.mapper.DividendIncomeRecordMapper;
import com.homewealth.mapper.InvestmentHoldingMapper;
import com.homewealth.market.ChinaFundFetcher;
import com.homewealth.market.DividendInfo;
import com.homewealth.market.YahooFinanceFetcher;
import com.homewealth.model.DividendEvent;
import com.homewealth.model.DividendIncomeRecord;
import com.homewealth.model.InvestmentHolding;
import com.homewealth.service.DividendService;
import com.homewealth.service.ExchangeRateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DividendServiceImpl implements DividendService {

    private final DividendEventMapper dividendEventMapper;
    private final DividendIncomeRecordMapper dividendIncomeRecordMapper;
    private final InvestmentHoldingMapper holdingMapper;
    private final YahooFinanceFetcher yahooFetcher;
    private final ChinaFundFetcher fundFetcher;
    private final ExchangeRateService exchangeRateService;

    @Override
    public void fetchAndStoreDividendEvents() {
        List<String> allSymbols = holdingMapper.findAllActiveSymbols();
        if (allSymbols.isEmpty()) {
            log.info("No active symbols, skipping dividend fetch");
            return;
        }

        // 分离基金和 Yahoo 标的，过滤掉汇率
        List<String> fundSymbols = allSymbols.stream().filter(this::isFundSymbol).toList();
        List<String> yahooSymbols = allSymbols.stream()
                .filter(s -> !isFundSymbol(s) && !s.endsWith("=X"))
                .toList();

        List<DividendEvent> allEvents = new ArrayList<>();

        // 抓取 Yahoo 分红（A股/港股/美股）
        if (!yahooSymbols.isEmpty()) {
            log.info("Fetching dividends from Yahoo for {} symbols", yahooSymbols.size());
            Map<String, List<DividendInfo>> yahooDividends = yahooFetcher.fetchDividends(yahooSymbols);
            for (Map.Entry<String, List<DividendInfo>> entry : yahooDividends.entrySet()) {
                for (DividendInfo info : entry.getValue()) {
                    allEvents.add(toDividendEvent(info, "YAHOO"));
                }
            }
        }

        // 抓取基金分红
        if (!fundSymbols.isEmpty()) {
            log.info("Fetching dividends from EastMoney for {} funds", fundSymbols.size());
            Map<String, List<DividendInfo>> fundDividends = fundFetcher.fetchDividends(fundSymbols);
            for (Map.Entry<String, List<DividendInfo>> entry : fundDividends.entrySet()) {
                for (DividendInfo info : entry.getValue()) {
                    allEvents.add(toDividendEvent(info, "EASTMONEY"));
                }
            }
        }

        if (allEvents.isEmpty()) {
            log.info("No dividend events found");
            return;
        }

        // 分批 upsert（避免单条 SQL 过长）
        int batchSize = 100;
        for (int i = 0; i < allEvents.size(); i += batchSize) {
            List<DividendEvent> batch = allEvents.subList(i, Math.min(i + batchSize, allEvents.size()));
            dividendEventMapper.batchUpsert(batch);
        }

        log.info("Stored {} dividend events", allEvents.size());
    }

    @Override
    public void generateDividendRecords(Long userId, LocalDate date) {
        List<InvestmentHolding> holdings = holdingMapper.findActiveByUserId(userId);
        if (holdings.isEmpty()) return;

        List<String> symbols = holdings.stream()
                .map(InvestmentHolding::getSymbol)
                .distinct()
                .toList();

        // 查找近3天的分红事件（防止某天漏掉）
        LocalDate startDate = date.minusDays(2);
        List<DividendEvent> events = dividendEventMapper.findBySymbolsAndDateRange(symbols, startDate, date);
        if (events.isEmpty()) return;

        List<DividendIncomeRecord> records = new ArrayList<>();

        for (DividendEvent event : events) {
            for (InvestmentHolding holding : holdings) {
                if (!holding.getSymbol().equals(event.getSymbol())) continue;

                BigDecimal quantity = holding.getQuantity();
                if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) continue;

                BigDecimal dividendAmount = quantity.multiply(event.getDividendPerShare())
                        .setScale(4, RoundingMode.HALF_UP);

                BigDecimal cnyRate = BigDecimal.ONE;
                BigDecimal dividendAmountCny = dividendAmount;

                if (!"CNY".equalsIgnoreCase(event.getCurrency())) {
                    cnyRate = exchangeRateService.getRate(event.getCurrency(), "CNY");
                    if (cnyRate == null || cnyRate.compareTo(BigDecimal.ZERO) <= 0) {
                        cnyRate = BigDecimal.ONE;
                    }
                    dividendAmountCny = dividendAmount.multiply(cnyRate).setScale(4, RoundingMode.HALF_UP);
                }

                DividendIncomeRecord record = new DividendIncomeRecord();
                record.setUserId(userId);
                record.setAccountId(holding.getAccountId());
                record.setSymbol(holding.getSymbol());
                record.setSymbolName(holding.getSymbolName());
                record.setMarket(holding.getMarket());
                record.setExDividendDate(event.getExDividendDate());
                record.setQuantity(quantity);
                record.setDividendPerShare(event.getDividendPerShare());
                record.setDividendAmount(dividendAmount);
                record.setCurrency(event.getCurrency());
                record.setCnyRate(cnyRate);
                record.setDividendAmountCny(dividendAmountCny);

                records.add(record);
            }
        }

        if (!records.isEmpty()) {
            dividendIncomeRecordMapper.batchUpsert(records);
            log.info("Generated {} dividend income records for userId={} date={}", records.size(), userId, date);
        }
    }

    @Override
    public void backfillDividendRecords(Long userId, int months) {
        // 1. 先抓取分红事件
        fetchAndStoreDividendEvents();

        // 2. 查找用户所有持仓
        List<InvestmentHolding> holdings = holdingMapper.findActiveByUserId(userId);
        if (holdings.isEmpty()) return;

        List<String> symbols = holdings.stream()
                .map(InvestmentHolding::getSymbol)
                .distinct()
                .toList();

        // 3. 查找日期范围内的所有分红事件
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusMonths(months);
        List<DividendEvent> events = dividendEventMapper.findBySymbolsAndDateRange(symbols, startDate, endDate);
        if (events.isEmpty()) {
            log.info("No dividend events found for backfill period {} to {}", startDate, endDate);
            return;
        }

        // 4. 为每个事件 × 持仓生成记录
        List<DividendIncomeRecord> records = new ArrayList<>();
        for (DividendEvent event : events) {
            for (InvestmentHolding holding : holdings) {
                if (!holding.getSymbol().equals(event.getSymbol())) continue;

                BigDecimal quantity = holding.getQuantity();
                if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) continue;

                BigDecimal dividendAmount = quantity.multiply(event.getDividendPerShare())
                        .setScale(4, RoundingMode.HALF_UP);

                BigDecimal cnyRate = BigDecimal.ONE;
                BigDecimal dividendAmountCny = dividendAmount;

                if (!"CNY".equalsIgnoreCase(event.getCurrency())) {
                    cnyRate = exchangeRateService.getRate(event.getCurrency(), "CNY");
                    if (cnyRate == null || cnyRate.compareTo(BigDecimal.ZERO) <= 0) {
                        cnyRate = BigDecimal.ONE;
                    }
                    dividendAmountCny = dividendAmount.multiply(cnyRate).setScale(4, RoundingMode.HALF_UP);
                }

                DividendIncomeRecord record = new DividendIncomeRecord();
                record.setUserId(userId);
                record.setAccountId(holding.getAccountId());
                record.setSymbol(holding.getSymbol());
                record.setSymbolName(holding.getSymbolName());
                record.setMarket(holding.getMarket());
                record.setExDividendDate(event.getExDividendDate());
                record.setQuantity(quantity);
                record.setDividendPerShare(event.getDividendPerShare());
                record.setDividendAmount(dividendAmount);
                record.setCurrency(event.getCurrency());
                record.setCnyRate(cnyRate);
                record.setDividendAmountCny(dividendAmountCny);

                records.add(record);
            }
        }

        if (!records.isEmpty()) {
            int batchSize = 100;
            for (int i = 0; i < records.size(); i += batchSize) {
                List<DividendIncomeRecord> batch = records.subList(i, Math.min(i + batchSize, records.size()));
                dividendIncomeRecordMapper.batchUpsert(batch);
            }
            log.info("Backfilled {} dividend income records for userId={} ({} months)", records.size(), userId, months);
        }
    }

    @Override
    public DividendSummaryVO getDividendSummary(Long userId) {
        LocalDate today = LocalDate.now();

        DividendSummaryVO vo = new DividendSummaryVO();
        vo.setLast12MonthsTotal(dividendIncomeRecordMapper.findTotal(userId, today.minusMonths(12), today));
        vo.setCurrentYearTotal(dividendIncomeRecordMapper.findTotal(userId,
                LocalDate.of(today.getYear(), 1, 1), today));
        vo.setCurrentMonthTotal(dividendIncomeRecordMapper.findTotal(userId,
                today.withDayOfMonth(1), today));

        return vo;
    }

    @Override
    public DividendHistoryVO getDividendHistory(Long userId, int months) {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusMonths(months);

        List<Map<String, Object>> rows = dividendIncomeRecordMapper.findMonthlyAggregation(userId, startDate, today);

        DividendHistoryVO vo = new DividendHistoryVO();
        List<String> monthList = new ArrayList<>();
        List<BigDecimal> totalList = new ArrayList<>();

        for (Map<String, Object> row : rows) {
            monthList.add((String) row.get("month"));
            Object total = row.get("total");
            totalList.add(total instanceof BigDecimal ? (BigDecimal) total : new BigDecimal(total.toString()));
        }

        vo.setMonths(monthList);
        vo.setTotals(totalList);
        return vo;
    }

    @Override
    public List<DividendDetailVO> getDividendDetail(Long userId, int year, Integer month) {
        LocalDate startDate;
        LocalDate endDate;

        if (month != null) {
            startDate = LocalDate.of(year, month, 1);
            endDate = startDate.plusMonths(1).minusDays(1);
        } else {
            startDate = LocalDate.of(year, 1, 1);
            endDate = LocalDate.of(year, 12, 31);
        }

        List<Map<String, Object>> rows = dividendIncomeRecordMapper.findDetailByPeriod(userId, startDate, endDate);

        List<DividendDetailVO> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            DividendDetailVO vo = new DividendDetailVO();
            vo.setSymbol((String) row.get("symbol"));
            vo.setSymbolName((String) row.get("symbolName"));
            vo.setMarket((String) row.get("market"));
            vo.setCurrency((String) row.get("currency"));

            Object totalCny = row.get("totalDividendCny");
            vo.setTotalDividendCny(totalCny instanceof BigDecimal ? (BigDecimal) totalCny
                    : new BigDecimal(totalCny.toString()));

            Object totalOrig = row.get("totalDividendOriginal");
            vo.setTotalDividendOriginal(totalOrig instanceof BigDecimal ? (BigDecimal) totalOrig
                    : new BigDecimal(totalOrig.toString()));

            Object count = row.get("eventCount");
            vo.setEventCount(count instanceof Number ? ((Number) count).intValue() : Integer.parseInt(count.toString()));

            result.add(vo);
        }

        return result;
    }

    // ---- 辅助方法 ----

    private DividendEvent toDividendEvent(DividendInfo info, String source) {
        DividendEvent event = new DividendEvent();
        event.setSymbol(info.getSymbol());
        event.setMarket(inferMarket(info.getSymbol()));
        event.setExDividendDate(info.getExDividendDate());
        event.setDividendPerShare(info.getDividendPerShare());
        event.setCurrency(info.getCurrency());
        event.setSource(source);
        return event;
    }

    private boolean isFundSymbol(String symbol) {
        return symbol != null && symbol.matches("\\d{6}");
    }

    private String inferMarket(String symbol) {
        if (isFundSymbol(symbol)) return "CN_FUND";
        if (symbol.endsWith("=X")) return "FX";
        if (symbol.endsWith(".SS") || symbol.endsWith(".SZ")) return "CN_A";
        if (symbol.endsWith(".HK")) return "HK";
        if (symbol.matches("[A-Z]+\\d{6}[CP]\\d{8}")) return "US_OPT";
        return "US";
    }
}
