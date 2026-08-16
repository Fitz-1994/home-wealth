package com.homewealth.market;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 联网冒烟测试：验证新浪 / 腾讯 数据源的真实响应能被正确解析。
 *
 * <p>依赖外网，默认随 {@code mvn test} 执行；网络不可达时会失败，属预期行为
 * —— 这些接口正是生产链路的唯一数据来源，跑不通就该及时暴露。
 */
class LiveFetcherSmokeTest {

    private final SinaFinanceFetcher sina = new SinaFinanceFetcher();
    private final MarketHistoryFetcher history = new MarketHistoryFetcher(new ObjectMapper());
    private final ChinaFundFetcher fund = new ChinaFundFetcher(new ObjectMapper());

    @Test
    void fetchesQuotesAcrossAllThreeMarkets() {
        List<String> symbols = List.of(
                "600519.SS", "000651.SZ", "510310.SS",   // A股 + ETF
                "0700.HK", "3690.HK",                    // 港股
                "AAPL", "VOO"                            // 美股
        );

        Map<String, MarketQuote> quotes = sina.fetchQuotes(symbols);

        assertEquals(symbols.size(), quotes.size(),
                () -> "缺失标的: " + symbols.stream().filter(s -> !quotes.containsKey(s)).toList());

        for (String symbol : symbols) {
            MarketQuote q = quotes.get(symbol);
            assertNotNull(q, symbol);
            assertTrue(q.getPrice().signum() > 0, symbol + " 价格应为正");
            assertNotNull(q.getTradeDate(), symbol + " 应有交易日");
            assertEquals("SINA", q.getSource());
            assertFalse(q.getSymbolName().isBlank(), symbol + " 应有名称");
            System.out.printf("%-12s %-14s %12s %s %s%n",
                    symbol, q.getSymbolName(), q.getPrice(), q.getCurrency(), q.getTradeDate());
        }

        assertEquals("CNY", quotes.get("600519.SS").getCurrency());
        assertEquals("HKD", quotes.get("0700.HK").getCurrency());
        assertEquals("USD", quotes.get("AAPL").getCurrency());
    }

    @Test
    void skipsOptionSymbolsInsteadOfFailing() {
        // 期权无免费源，应被静默跳过而不是拖垮整批请求
        Map<String, MarketQuote> quotes = sina.fetchQuotes(List.of(
                "600519.SS", "TCH261230P400.HK", "VOO280121P00445000"));

        assertTrue(quotes.containsKey("600519.SS"), "股票仍应返回");
        assertFalse(quotes.containsKey("TCH261230P400.HK"), "港股期权应跳过");
        assertFalse(quotes.containsKey("VOO280121P00445000"), "美股期权应跳过");
    }

    /**
     * 美股行情的交易日必须是美股当地收盘日，而不是北京时间日期。
     * 新浪 gb_ 报文的时间戳字段是北京时间，美股收盘后会落到次日 —— 直接取它会让
     * 交易日整体记晚一天，进而让"价格过期 N 天"少算一天。
     */
    @Test
    void usQuoteUsesLocalTradingDateNotBeijingDate() {
        MarketQuote aapl = sina.fetchQuotes(List.of("AAPL")).get("AAPL");
        assertNotNull(aapl);

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        assertTrue(aapl.getTradeDate().isBefore(today.plusDays(1)),
                "美股交易日不应晚于今天，实际 " + aapl.getTradeDate());

        // 与历史日K的最后一个交易日对齐 —— 两者取的应是同一个美股交易日
        List<DailyClose> hist = history.fetchDailyCloses("AAPL", 3);
        assertFalse(hist.isEmpty());
        assertEquals(hist.get(hist.size() - 1).getDate(), aapl.getTradeDate(),
                "实时行情与历史日K的交易日应一致");
    }

    /**
     * 场外基金：主源 fundgz 会以 HTTP 200 返回 HTML 错误页，
     * 解析异常必须被吞掉并走 lsjz 兜底，否则主源一坏全部基金判为过期。
     */
    @Test
    void fundFallsBackToLsjzWhenPrimarySourceIsBroken() {
        List<String> funds = List.of("017386", "110017");
        Map<String, MarketQuote> quotes = fund.fetchQuotes(funds);

        for (String code : funds) {
            MarketQuote q = quotes.get(code);
            assertNotNull(q, code + " 应通过兜底取到净值");
            assertTrue(q.getPrice().signum() > 0, code + " 净值应为正");
            System.out.printf("%-10s %-20s %s @ %s%n",
                    code, q.getSymbolName(), q.getPrice(), q.getTradeDate());
        }
    }

    @Test
    void fetchesFxRates() {
        Map<String, BigDecimal> rates = sina.fetchFxRates();

        for (String ccy : List.of("USD", "HKD", "EUR", "GBP", "JPY")) {
            BigDecimal r = rates.get(ccy);
            assertNotNull(r, ccy + " 汇率缺失");
            assertTrue(r.signum() > 0, ccy + " 汇率应为正");
            System.out.printf("1 %s = %s CNY%n", ccy, r);
        }
        // 量级合理性：USD/CNY 落在 5~10，HKD/CNY 落在 0.5~1.5
        assertTrue(rates.get("USD").compareTo(new BigDecimal("5")) > 0
                && rates.get("USD").compareTo(new BigDecimal("10")) < 0, "USD 汇率量级异常");
        assertTrue(rates.get("HKD").compareTo(new BigDecimal("0.5")) > 0
                && rates.get("HKD").compareTo(new BigDecimal("1.5")) < 0, "HKD 汇率量级异常");
    }

    @Test
    void fetchesDailyHistoryForEachMarket() {
        for (String symbol : List.of("600519.SS", "0700.HK", "AAPL")) {
            List<DailyClose> closes = history.fetchDailyCloses(symbol, 8);
            assertFalse(closes.isEmpty(), symbol + " 应有历史数据");
            assertTrue(closes.get(0).getDate().isBefore(closes.get(closes.size() - 1).getDate()),
                    symbol + " 应按日期升序");
            closes.forEach(c -> assertTrue(c.getClose().signum() > 0));
            System.out.printf("%-12s n=%d  %s..%s  last=%s%n", symbol, closes.size(),
                    closes.get(0).getDate(), closes.get(closes.size() - 1).getDate(),
                    closes.get(closes.size() - 1).getClose());
        }
    }

    /**
     * 基准指数必须与库中既有的 Yahoo 序列同口径。
     * 美股指数尤其关键：新浪实时行情的 int_sp500 / int_nasdaq 是另一套口径（约低 15%），
     * 一旦混用会在收益对比曲线上造出假跳空，故此处锁定量级。
     */
    @Test
    void fetchesBenchmarkIndicesOnYahooCompatibleScale() {
        for (String symbol : MarketHistoryFetcher.supportedIndexSymbols()) {
            List<DailyClose> closes = history.fetchDailyCloses(symbol, 8);
            assertFalse(closes.isEmpty(), symbol + " 应有历史数据");
            System.out.printf("%-12s n=%d last=%s @ %s%n", symbol, closes.size(),
                    closes.get(closes.size() - 1).getClose(),
                    closes.get(closes.size() - 1).getDate());
        }

        BigDecimal gspc = last(history.fetchDailyCloses("^GSPC", 3));
        BigDecimal ixic = last(history.fetchDailyCloses("^IXIC", 3));
        BigDecimal hsi = last(history.fetchDailyCloses("^HSI", 3));
        BigDecimal csi300 = last(history.fetchDailyCloses("000300.SS", 3));

        // 库中 2026-08-04 收盘：^GSPC 7736 / ^IXIC 26585 / ^HSI 25853 / 000300.SS 4601
        assertBetween(gspc, "6500", "9500", "^GSPC");
        assertBetween(ixic, "22000", "32000", "^IXIC");
        assertBetween(hsi, "20000", "32000", "^HSI");
        assertBetween(csi300, "3500", "6000", "000300.SS");
    }

    private static BigDecimal last(List<DailyClose> closes) {
        assertFalse(closes.isEmpty());
        return closes.get(closes.size() - 1).getClose();
    }

    private static void assertBetween(BigDecimal v, String lo, String hi, String label) {
        assertTrue(v.compareTo(new BigDecimal(lo)) > 0 && v.compareTo(new BigDecimal(hi)) < 0,
                () -> label + " 点位 " + v + " 超出合理区间 [" + lo + ", " + hi + "]，可能取到了错误口径的指数");
    }
}
