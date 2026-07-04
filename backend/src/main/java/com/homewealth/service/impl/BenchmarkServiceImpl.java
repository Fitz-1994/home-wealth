package com.homewealth.service.impl;

import com.homewealth.dto.response.BenchmarkInfoVO;
import com.homewealth.dto.response.BenchmarkReturnVO;
import com.homewealth.dto.response.BenchmarkSeriesVO;
import com.homewealth.mapper.BenchmarkQuoteMapper;
import com.homewealth.mapper.DailyInvestmentSnapshotMapper;
import com.homewealth.market.DailyClose;
import com.homewealth.market.YahooFinanceFetcher;
import com.homewealth.model.BenchmarkQuote;
import com.homewealth.service.BenchmarkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基准指数服务 —— 抓取沪深300等指数收盘点位，计算周期收益率用于对比。
 * 指数无现金流，周期收益率 = 期末点位 / 期初点位 - 1（每日链式 telescoping）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BenchmarkServiceImpl implements BenchmarkService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int PCT_SCALE = 4;

    /** 每日调度抓取范围：近 5 个交易日，自动补齐节假日/漏抓缺口 */
    private static final String DAILY_RANGE = "5d";
    /** 组合无快照时的回填兜底起始（近 3 个月） */
    private static final int FALLBACK_MONTHS = 3;

    /** 已配置的基准指数：symbol -> 展示名（各大市场主流指数） */
    private static final Map<String, String> BENCHMARKS = new LinkedHashMap<>() {{
        put("000300.SS", "沪深300");
        put("000001.SS", "上证指数");
        put("^HSI", "恒生指数");
        put("^GSPC", "标普500");
        put("^IXIC", "纳斯达克");
    }};

    private final BenchmarkQuoteMapper benchmarkMapper;
    private final DailyInvestmentSnapshotMapper snapshotMapper;
    private final YahooFinanceFetcher yahooFetcher;

    @Override
    public void fetchAndSaveAll() {
        // 每日增量：只抓近几日，无需下限过滤（都在组合起始日之后）
        fetchAndSaveRange(DAILY_RANGE, null);
    }

    @Override
    public void backfillHistory() {
        // 回填对齐组合数据起始日：只补该日之后的指数行情，并清理更早的历史
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        LocalDate inception = snapshotMapper.findEarliestDate();
        if (inception == null) {
            inception = today.minusMonths(FALLBACK_MONTHS);
        }
        String range = pickRange(inception, today);
        log.info("[Benchmark] backfill from inception={} (range={})", inception, range);
        fetchAndSaveRange(range, inception);
        benchmarkMapper.deleteBefore(inception);
    }

    /** 选取能覆盖 [from, today] 的最小 Yahoo range 档位 */
    private String pickRange(LocalDate from, LocalDate today) {
        long days = ChronoUnit.DAYS.between(from, today) + 10;  // 留 10 天缓冲
        if (days <= 30) return "1mo";
        if (days <= 90) return "3mo";
        if (days <= 180) return "6mo";
        if (days <= 365) return "1y";
        if (days <= 730) return "2y";
        if (days <= 1825) return "5y";
        return "max";
    }

    /** 抓取并入库；minDate 非空时仅保留该日期（含）之后的数据点 */
    private void fetchAndSaveRange(String range, LocalDate minDate) {
        for (Map.Entry<String, String> e : BENCHMARKS.entrySet()) {
            String symbol = e.getKey();
            try {
                List<DailyClose> closes = yahooFetcher.fetchDailyCloses(symbol, range);
                if (closes.isEmpty()) {
                    log.warn("[Benchmark] no history for {} (range={})", symbol, range);
                    continue;
                }
                int saved = 0;
                for (DailyClose dc : closes) {
                    if (minDate != null && dc.getDate().isBefore(minDate)) continue;
                    BenchmarkQuote bq = new BenchmarkQuote();
                    bq.setSymbol(symbol);
                    bq.setName(e.getValue());
                    bq.setQuoteDate(dc.getDate());
                    bq.setClose(dc.getClose());
                    bq.setCurrency(dc.getCurrency() != null ? dc.getCurrency() : "CNY");
                    bq.setSource("YAHOO");
                    benchmarkMapper.upsert(bq);
                    saved++;
                }
                log.info("[Benchmark] saved {} points for {} (range={}, latest={})",
                        saved, symbol, range, closes.get(closes.size() - 1).getDate());
            } catch (Exception ex) {
                log.error("[Benchmark] fetch failed for {}: {}", symbol, ex.getMessage());
            }
        }
    }

    @Override
    public List<BenchmarkInfoVO> listBenchmarks() {
        List<BenchmarkInfoVO> result = new ArrayList<>();
        for (Map.Entry<String, String> e : BENCHMARKS.entrySet()) {
            BenchmarkInfoVO vo = new BenchmarkInfoVO();
            vo.setSymbol(e.getKey());
            vo.setName(e.getValue());
            List<BenchmarkQuote> quotes = benchmarkMapper.findBySymbol(e.getKey());
            if (!quotes.isEmpty()) {
                BenchmarkQuote latest = quotes.get(quotes.size() - 1);
                vo.setLatestClose(latest.getClose());
                vo.setLatestDate(latest.getQuoteDate());
            }
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<BenchmarkReturnVO> getReturns(String symbol, String granularity, Integer year) {
        List<BenchmarkQuote> quotes = benchmarkMapper.findBySymbol(symbol);
        if (quotes.isEmpty()) return new ArrayList<>();

        boolean yearly = "yearly".equalsIgnoreCase(granularity);

        // 按周期分组（保持时间顺序）
        Map<String, List<BenchmarkQuote>> grouped = new LinkedHashMap<>();
        for (BenchmarkQuote q : quotes) {
            LocalDate d = q.getQuoteDate();
            String key = yearly
                    ? String.valueOf(d.getYear())
                    : String.format("%04d-%02d", d.getYear(), d.getMonthValue());
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(q);
        }

        List<BenchmarkReturnVO> result = new ArrayList<>();
        BigDecimal prevPeriodLastClose = null;
        for (Map.Entry<String, List<BenchmarkQuote>> e : grouped.entrySet()) {
            List<BenchmarkQuote> list = e.getValue();
            BigDecimal endClose = list.get(list.size() - 1).getClose();
            BigDecimal beginClose = prevPeriodLastClose != null
                    ? prevPeriodLastClose
                    : list.get(0).getClose();
            prevPeriodLastClose = endClose;

            if (!yearly && year != null) {
                int y = Integer.parseInt(e.getKey().substring(0, 4));
                if (y != year) continue;
            }

            BigDecimal returnPct = beginClose.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : endClose.divide(beginClose, 8, RoundingMode.HALF_UP)
                        .subtract(BigDecimal.ONE)
                        .multiply(HUNDRED)
                        .setScale(PCT_SCALE, RoundingMode.HALF_UP);

            BenchmarkReturnVO vo = new BenchmarkReturnVO();
            vo.setPeriod(e.getKey());
            vo.setReturnPct(returnPct);
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<BenchmarkSeriesVO> getDailySeries(LocalDate from, LocalDate to) {
        List<BenchmarkSeriesVO> result = new ArrayList<>();
        for (Map.Entry<String, String> e : BENCHMARKS.entrySet()) {
            BenchmarkSeriesVO vo = new BenchmarkSeriesVO();
            vo.setSymbol(e.getKey());
            vo.setName(e.getValue());
            List<BenchmarkSeriesVO.Point> points = new ArrayList<>();
            for (BenchmarkQuote q : benchmarkMapper.findBySymbol(e.getKey())) {
                LocalDate d = q.getQuoteDate();
                if (from != null && d.isBefore(from)) continue;
                if (to != null && d.isAfter(to)) continue;
                BenchmarkSeriesVO.Point p = new BenchmarkSeriesVO.Point();
                p.setDate(d);
                p.setClose(q.getClose());
                points.add(p);
            }
            vo.setPoints(points);
            result.add(vo);
        }
        return result;
    }
}
