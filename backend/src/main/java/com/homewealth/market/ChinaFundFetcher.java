package com.homewealth.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 天天基金（东方财富）公募基金净值获取器
 *
 * 数据源优先级：
 * 1. fundgz API（盘中估值 + 确认净值）— 大部分非 QDII 基金支持
 * 2. lsjz API（历史净值）— 所有基金支持，用作兜底
 *
 * Symbol 格式：6位基金代码（如 006327、001550）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChinaFundFetcher {

    private static final String FUNDGZ_URL = "http://fundgz.1234567.com.cn/js/%s.js";
    private static final String LSJZ_URL = "https://api.fund.eastmoney.com/f10/lsjz?fundCode=%s&pageIndex=1&pageSize=2";
    private static final Pattern JSONP_PATTERN = Pattern.compile("jsonpgz\\((.+?)\\);?");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * 批量获取基金净值（并发请求）
     */
    public Map<String, MarketQuote> fetchQuotes(List<String> fundCodes) {
        if (fundCodes == null || fundCodes.isEmpty()) return Collections.emptyMap();

        List<CompletableFuture<Optional<MarketQuote>>> futures = fundCodes.stream()
                .map(code -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return fetchOne(code);
                    } catch (Exception e) {
                        log.error("Failed to fetch fund NAV for {}: {}", code, e.getMessage());
                        return Optional.<MarketQuote>empty();
                    }
                }))
                .collect(Collectors.toList());

        Map<String, MarketQuote> result = new HashMap<>();
        for (int i = 0; i < fundCodes.size(); i++) {
            futures.get(i).join().ifPresent(q -> result.put(q.getSymbol(), q));
        }
        return result;
    }

    public Optional<MarketQuote> fetchSingle(String fundCode) {
        try {
            return fetchOne(fundCode);
        } catch (Exception e) {
            log.error("Failed to fetch fund NAV for {}: {}", fundCode, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<MarketQuote> fetchOne(String fundCode) throws Exception {
        // 先尝试 fundgz API（支持盘中估值）
        Optional<MarketQuote> result = fetchFromFundgz(fundCode);
        if (result.isPresent()) return result;

        // 兜底：使用 lsjz API（历史确认净值，所有基金都支持）
        log.debug("Falling back to lsjz API for fund {}", fundCode);
        return fetchFromLsjz(fundCode);
    }

    // ---- fundgz API（盘中估值） ----

    private Optional<MarketQuote> fetchFromFundgz(String fundCode) throws Exception {
        String url = String.format(FUNDGZ_URL, fundCode);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)")
                .header("Referer", "http://fund.eastmoney.com/")
                .timeout(Duration.ofSeconds(8))
                .GET().build();

        HttpResponse<String> response = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) return Optional.empty();

        String body = response.body();
        Matcher matcher = JSONP_PATTERN.matcher(body);
        if (!matcher.find()) return Optional.empty();

        String json = matcher.group(1);
        if (json.isEmpty() || json.equals("{}")) return Optional.empty();

        JsonNode node = objectMapper.readTree(json);
        String name = node.path("name").asText("");
        String dwjzStr = node.path("dwjz").asText("");
        String jzrqStr = node.path("jzrq").asText("");
        String gszzlStr = node.path("gszzl").asText("");

        if (dwjzStr.isEmpty()) return Optional.empty();

        BigDecimal nav = new BigDecimal(dwjzStr);
        if (nav.compareTo(BigDecimal.ZERO) <= 0) return Optional.empty();

        LocalDate tradeDate = jzrqStr.isEmpty() ? LocalDate.now() : LocalDate.parse(jzrqStr, DATE_FMT);

        BigDecimal changePct = BigDecimal.ZERO;
        if (!gszzlStr.isEmpty()) {
            try {
                changePct = new BigDecimal(gszzlStr).setScale(4, RoundingMode.HALF_UP);
            } catch (NumberFormatException ignored) {}
        }

        return Optional.of(MarketQuote.builder()
                .symbol(fundCode)
                .symbolName(name)
                .price(nav)
                .currency("CNY")
                .changePct(changePct)
                .tradeDate(tradeDate)
                .source("EASTMONEY")
                .build());
    }

    // ---- lsjz API（历史确认净值，兜底） ----

    private Optional<MarketQuote> fetchFromLsjz(String fundCode) throws Exception {
        String url = String.format(LSJZ_URL, fundCode);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)")
                .header("Referer", "https://fund.eastmoney.com/")
                .timeout(Duration.ofSeconds(8))
                .GET().build();

        HttpResponse<String> response = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) return Optional.empty();

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode list = root.path("Data").path("LSJZList");
        if (!list.isArray() || list.isEmpty()) return Optional.empty();

        JsonNode latest = list.get(0);
        String dwjzStr = latest.path("DWJZ").asText("");
        String dateStr = latest.path("FSRQ").asText("");
        String jzzzlStr = latest.path("JZZZL").asText("");

        if (dwjzStr.isEmpty()) return Optional.empty();

        BigDecimal nav = new BigDecimal(dwjzStr);
        if (nav.compareTo(BigDecimal.ZERO) <= 0) return Optional.empty();

        LocalDate tradeDate = dateStr.isEmpty() ? LocalDate.now() : LocalDate.parse(dateStr, DATE_FMT);

        BigDecimal changePct = BigDecimal.ZERO;
        if (!jzzzlStr.isEmpty()) {
            try {
                changePct = new BigDecimal(jzzzlStr).setScale(4, RoundingMode.HALF_UP);
            } catch (NumberFormatException ignored) {}
        }

        // lsjz API 不返回基金名称，使用基金代码搜索获取
        String name = fetchFundName(fundCode);

        return Optional.of(MarketQuote.builder()
                .symbol(fundCode)
                .symbolName(name != null ? name : fundCode)
                .price(nav)
                .currency("CNY")
                .changePct(changePct)
                .tradeDate(tradeDate)
                .source("EASTMONEY")
                .build());
    }

    /**
     * 通过基金搜索 API 获取基金名称
     */
    private String fetchFundName(String fundCode) {
        try {
            String url = "https://fundsuggest.eastmoney.com/FundSearch/api/FundSearchAPI.ashx?m=1&key=" + fundCode;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)")
                    .header("Referer", "https://fund.eastmoney.com/")
                    .timeout(Duration.ofSeconds(5))
                    .GET().build();

            HttpResponse<String> response = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return null;

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode datas = root.path("Datas");
            if (datas.isArray() && !datas.isEmpty()) {
                for (JsonNode item : datas) {
                    if (fundCode.equals(item.path("CODE").asText(""))) {
                        return item.path("NAME").asText(null);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to fetch fund name for {}: {}", fundCode, e.getMessage());
        }
        return null;
    }
}
