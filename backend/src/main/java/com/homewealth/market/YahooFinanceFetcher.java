package com.homewealth.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Yahoo Finance 行情接口适配器
 *
 * 使用 v8/finance/chart 接口（无需 crumb / Cookie 认证）。
 * 每个 symbol 独立请求，通过 CompletableFuture 并发执行。
 *
 * Symbol 格式：
 * - A股上交所: 600519.SS   A股深交所: 000858.SZ
 * - 港股:      0700.HK
 * - 美股:      AAPL / VOO
 * - 汇率:      USDCNY=X
 * - 美股期权:  AAPL250321C00150000（OCC格式）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class YahooFinanceFetcher {

    private final ObjectMapper objectMapper;

    @Value("${market.yahoo.user-agent}")
    private String userAgent;

    private static final String BASE_URL = "https://query2.finance.yahoo.com";
    private static final String CHART_PATH = "/v8/finance/chart/%s?interval=1d&range=1d";

    private HttpClient httpClient;

    @PostConstruct
    void init() {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    // ---- 公开接口 ----

    public Map<String, MarketQuote> fetchQuotes(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) return Collections.emptyMap();

        // 并发请求
        List<CompletableFuture<Optional<MarketQuote>>> futures = symbols.stream()
                .map(sym -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return fetchOne(sym);
                    } catch (Exception e) {
                        log.error("Failed to fetch quote for {}: {}", sym, e.getMessage());
                        return Optional.<MarketQuote>empty();
                    }
                }))
                .collect(Collectors.toList());

        Map<String, MarketQuote> result = new HashMap<>();
        for (int i = 0; i < symbols.size(); i++) {
            futures.get(i).join().ifPresent(q -> result.put(q.getSymbol(), q));
        }
        return result;
    }

    public Optional<MarketQuote> fetchSingle(String symbol) {
        try {
            return fetchOne(symbol);
        } catch (Exception e) {
            log.error("Failed to fetch quote for {}: {}", symbol, e.getMessage());
            return Optional.empty();
        }
    }

    // ---- 内部实现 ----

    private Optional<MarketQuote> fetchOne(String symbol) throws Exception {
        String url = BASE_URL + String.format(CHART_PATH, symbol);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .header("Accept-Language", "en-US,en;q=0.9")
                .timeout(Duration.ofSeconds(10))
                .GET().build();

        HttpResponse<String> response = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.warn("Yahoo Finance chart API HTTP {} for symbol {}", response.statusCode(), symbol);
            return Optional.empty();
        }

        return parseChartResponse(symbol, response.body());
    }

    private Optional<MarketQuote> parseChartResponse(String symbol, String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode chart = root.path("chart");

        JsonNode errorNode = chart.path("error");
        if (!errorNode.isNull()) {
            log.warn("Yahoo Finance chart error for {}: {}", symbol, errorNode);
            return Optional.empty();
        }

        JsonNode results = chart.path("result");
        if (!results.isArray() || results.isEmpty()) {
            log.warn("Empty chart result for symbol: {}", symbol);
            return Optional.empty();
        }

        JsonNode meta = results.get(0).path("meta");
        double price = meta.path("regularMarketPrice").asDouble(0);
        if (price == 0) {
            log.warn("Zero price for symbol: {}", symbol);
            return Optional.empty();
        }

        String currency = meta.path("currency").asText("USD");
        String name = meta.path("shortName").asText(symbol);
        if (name.isEmpty()) name = meta.path("longName").asText(symbol);

        // 涨跌幅 = (现价 - 前收盘) / 前收盘 * 100
        double prevClose = meta.path("chartPreviousClose").asDouble(price);
        double changePct = prevClose > 0
                ? (price - prevClose) / prevClose * 100
                : 0;

        return Optional.of(MarketQuote.builder()
                .symbol(symbol)
                .symbolName(name)
                .price(BigDecimal.valueOf(price))
                .currency(currency)
                .changePct(BigDecimal.valueOf(changePct).setScale(4, RoundingMode.HALF_UP))
                .tradeDate(LocalDate.now())
                .source("YAHOO")
                .build());
    }
}
