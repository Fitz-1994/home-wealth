package com.homewealth.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Yahoo Finance 非官方行情接口适配器
 *
 * Symbol 格式规范：
 * - A股上交所: 600519.SS
 * - A股深交所: 000858.SZ
 * - 港股:      0700.HK
 * - 美股:      AAPL
 * - 汇率:      USDCNY=X
 * - 美股期权:  AAPL250321C00150000（OCC格式）
 * - 港股期权:  HSI250328C24000.HK
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class YahooFinanceFetcher {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${market.yahoo.base-url}")
    private String baseUrl;

    @Value("${market.yahoo.user-agent}")
    private String userAgent;

    // Yahoo Finance v7 quote API，支持批量（最多20个symbol）
    private static final String QUOTE_URL =
            "/v7/finance/quote?symbols={symbols}&fields=regularMarketPrice,currency,shortName,regularMarketChangePercent";

    public Map<String, MarketQuote> fetchQuotes(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return Collections.emptyMap();
        }

        // 每批最多20个
        Map<String, MarketQuote> result = new HashMap<>();
        List<List<String>> batches = partition(symbols, 20);

        for (List<String> batch : batches) {
            try {
                result.putAll(fetchBatch(batch));
            } catch (Exception e) {
                log.error("Yahoo Finance batch fetch failed for {}: {}", batch, e.getMessage());
                // 标记这批为 stale，不影响其他批次
            }
        }
        return result;
    }

    private Map<String, MarketQuote> fetchBatch(List<String> symbols) throws Exception {
        String symbolsStr = String.join(",", symbols);
        String url = baseUrl + QUOTE_URL;

        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", userAgent);
        headers.set("Accept", "application/json");
        headers.set("Accept-Language", "en-US,en;q=0.9");

        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class,
                symbolsStr
        );

        return parseQuoteResponse(response.getBody());
    }

    private Map<String, MarketQuote> parseQuoteResponse(String json) throws Exception {
        Map<String, MarketQuote> result = new HashMap<>();
        JsonNode root = objectMapper.readTree(json);
        JsonNode quoteResponse = root.path("quoteResponse");
        JsonNode errorNode = quoteResponse.path("error");
        if (!errorNode.isNull() && errorNode.has("description")) {
            log.warn("Yahoo Finance API error: {}", errorNode.path("description").asText());
        }

        JsonNode results = quoteResponse.path("result");
        if (results.isArray()) {
            for (JsonNode item : results) {
                String symbol = item.path("symbol").asText();
                double price = item.path("regularMarketPrice").asDouble(0);
                if (price == 0) {
                    log.warn("Zero price for symbol: {}", symbol);
                    continue;
                }
                String currency = item.path("currency").asText("USD");
                String name = item.path("shortName").asText(symbol);
                double changePct = item.path("regularMarketChangePercent").asDouble(0);

                MarketQuote quote = MarketQuote.builder()
                        .symbol(symbol)
                        .symbolName(name)
                        .price(BigDecimal.valueOf(price))
                        .currency(currency)
                        .changePct(BigDecimal.valueOf(changePct))
                        .tradeDate(LocalDate.now())
                        .source("YAHOO")
                        .build();
                result.put(symbol, quote);
            }
        }
        return result;
    }

    public Optional<MarketQuote> fetchSingle(String symbol) {
        Map<String, MarketQuote> quotes = fetchQuotes(List.of(symbol));
        return Optional.ofNullable(quotes.get(symbol));
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }
}
