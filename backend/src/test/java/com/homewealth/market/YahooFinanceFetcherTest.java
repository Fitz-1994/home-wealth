package com.homewealth.market;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for YahooFinanceFetcher JSON parsing logic.
 * Uses recorded v8/finance/chart response fixtures.
 */
class YahooFinanceFetcherTest {

    private YahooFinanceFetcher fetcher;

    // Fixture: typical v8/finance/chart response for AAPL
    private static final String CHART_AAPL = """
            {
              "chart": {
                "result": [{
                  "meta": {
                    "currency": "USD",
                    "symbol": "AAPL",
                    "shortName": "Apple Inc.",
                    "regularMarketPrice": 257.46,
                    "chartPreviousClose": 260.29
                  }
                }],
                "error": null
              }
            }
            """;

    // Fixture: 港股
    private static final String CHART_HK = """
            {
              "chart": {
                "result": [{
                  "meta": {
                    "currency": "HKD",
                    "symbol": "0700.HK",
                    "shortName": "TENCENT",
                    "regularMarketPrice": 432.0,
                    "chartPreviousClose": 430.8
                  }
                }],
                "error": null
              }
            }
            """;

    // Fixture: zero price (should return empty)
    private static final String CHART_ZERO_PRICE = """
            {
              "chart": {
                "result": [{
                  "meta": {
                    "currency": "USD",
                    "symbol": "INVALID",
                    "shortName": "",
                    "regularMarketPrice": 0,
                    "chartPreviousClose": 0
                  }
                }],
                "error": null
              }
            }
            """;

    // Fixture: API error
    private static final String CHART_ERROR = """
            {
              "chart": {
                "result": null,
                "error": {
                  "code": "Not Found",
                  "description": "No fundamentals data found"
                }
              }
            }
            """;

    // Fixture: empty result array
    private static final String CHART_EMPTY = """
            {
              "chart": {
                "result": [],
                "error": null
              }
            }
            """;

    @BeforeEach
    void setUp() {
        fetcher = new YahooFinanceFetcher(new ObjectMapper());
    }

    @Test
    @DisplayName("parseChartResponse - 解析美股行情，涨跌幅计算正确")
    void testParseChartResponse_aapl() throws Exception {
        Optional<MarketQuote> result = invokeParseChartResponse("AAPL", CHART_AAPL);

        assertThat(result).isPresent();
        MarketQuote q = result.get();
        assertThat(q.getSymbol()).isEqualTo("AAPL");
        assertThat(q.getPrice()).isEqualByComparingTo("257.46");
        assertThat(q.getCurrency()).isEqualTo("USD");
        assertThat(q.getSymbolName()).isEqualTo("Apple Inc.");
        // changePct = (257.46 - 260.29) / 260.29 * 100 ≈ -1.0872
        assertThat(q.getChangePct().doubleValue()).isLessThan(0);
    }

    @Test
    @DisplayName("parseChartResponse - 解析港股行情")
    void testParseChartResponse_hk() throws Exception {
        Optional<MarketQuote> result = invokeParseChartResponse("0700.HK", CHART_HK);

        assertThat(result).isPresent();
        assertThat(result.get().getCurrency()).isEqualTo("HKD");
        assertThat(result.get().getChangePct().doubleValue()).isGreaterThan(0);
    }

    @Test
    @DisplayName("parseChartResponse - 价格为0时返回empty")
    void testParseChartResponse_zeroPrice() throws Exception {
        Optional<MarketQuote> result = invokeParseChartResponse("INVALID", CHART_ZERO_PRICE);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("parseChartResponse - API错误时返回empty")
    void testParseChartResponse_apiError() throws Exception {
        Optional<MarketQuote> result = invokeParseChartResponse("X", CHART_ERROR);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("parseChartResponse - 空结果数组返回empty")
    void testParseChartResponse_emptyResult() throws Exception {
        Optional<MarketQuote> result = invokeParseChartResponse("X", CHART_EMPTY);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("fetchQuotes - 空列表直接返回空Map")
    void testFetchQuotes_emptyList() {
        assertThat(fetcher.fetchQuotes(List.of())).isEmpty();
    }

    @Test
    @DisplayName("fetchQuotes - null列表直接返回空Map")
    void testFetchQuotes_nullList() {
        assertThat(fetcher.fetchQuotes(null)).isEmpty();
    }

    // ---- Reflection helpers ----

    @SuppressWarnings("unchecked")
    private Optional<MarketQuote> invokeParseChartResponse(String symbol, String json) throws Exception {
        Method m = YahooFinanceFetcher.class.getDeclaredMethod("parseChartResponse", String.class, String.class);
        m.setAccessible(true);
        return (Optional<MarketQuote>) m.invoke(fetcher, symbol, json);
    }
}
