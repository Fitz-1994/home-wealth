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
import java.util.*;

/**
 * 历史日线收盘价抓取器 —— 用于基准指数曲线与快照回补。
 *
 * <p>按市场路由到不同数据源（均为国内可直连、实测稳定的接口）：
 * <ul>
 *   <li>A股 / A股指数 —— 新浪 {@code CN_MarketDataService.getKLineData}</li>
 *   <li>港股 / 恒生指数 —— 腾讯 {@code ifzq fqkline}</li>
 *   <li>美股 / 美股指数 —— 新浪 {@code US_MinKService.getDailyK}</li>
 * </ul>
 *
 * <p>⚠️ 美股指数必须走新浪 {@code .INX} / {@code .IXIC} 日K口径 —— 它与库中历史
 * （原 Yahoo ^GSPC / ^IXIC）连续。新浪实时行情里的 {@code int_sp500} /
 * {@code int_nasdaq} 是另一套口径（约低 15%），混用会在基准曲线上造出假跳空。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketHistoryFetcher {

    private final ObjectMapper objectMapper;

    private static final String SINA_CN_KLINE =
            "https://quotes.sina.cn/cn/api/json_v2.php/CN_MarketDataService.getKLineData"
                    + "?symbol=%s&scale=240&ma=no&datalen=%d";
    private static final String SINA_US_KLINE =
            "https://stock.finance.sina.com.cn/usstock/api/jsonp.php/var%%20_h=/US_MinKService.getDailyK"
                    + "?symbol=%s&___qn=3";
    private static final String TENCENT_KLINE =
            "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?param=%s,day,,,%d,qfq";

    /** 指数 symbol → 数据源代码（Yahoo 格式沿用为库内主键） */
    private static final Map<String, String> INDEX_SOURCE = Map.of(
            "000300.SS", "sh000300",
            "000001.SS", "sh000001",
            "^HSI", "hkHSI",
            "^GSPC", ".INX",
            "^IXIC", ".IXIC"
    );

    private HttpClient httpClient;

    private HttpClient client() {
        if (httpClient == null) {
            httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
        }
        return httpClient;
    }

    /**
     * 抓取指定标的近 N 个交易日的收盘价（按日期升序）。
     *
     * @param symbol Yahoo 格式代码，支持个股与已配置的指数
     * @param days   需要的交易日数量
     */
    public List<DailyClose> fetchDailyCloses(String symbol, int days) {
        if (symbol == null || symbol.isBlank() || days <= 0) return Collections.emptyList();

        try {
            String indexCode = INDEX_SOURCE.get(symbol);
            if (indexCode != null) {
                return fetchByCode(indexCode, days, currencyForIndex(symbol));
            }
            if (symbol.endsWith(".SS")) {
                return fetchSinaCn("sh" + strip(symbol), days, "CNY");
            }
            if (symbol.endsWith(".SZ")) {
                return fetchSinaCn("sz" + strip(symbol), days, "CNY");
            }
            if (symbol.endsWith(".HK")) {
                String code = strip(symbol);
                // 仅纯数字为正股；含字母的是期权（如 TCH261230P400.HK），无免费源
                if (!code.matches("\\d+")) return Collections.emptyList();
                while (code.length() < 5) code = "0" + code;
                return fetchTencent("hk" + code, days, "HKD");
            }
            if (symbol.matches("[A-Z]+\\d{6}[CP]\\d{8}")) {
                return Collections.emptyList();   // 美股期权无免费源
            }
            if (symbol.matches("\\d{6}")) {
                return Collections.emptyList();   // 场外基金另由 ChinaFundFetcher 处理
            }
            return fetchSinaUs(symbol, days, "USD");
        } catch (Exception e) {
            log.error("History fetch failed for {}: {}", symbol, e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 已支持历史回补的基准指数 */
    public static Set<String> supportedIndexSymbols() {
        return INDEX_SOURCE.keySet();
    }

    private List<DailyClose> fetchByCode(String code, int days, String currency) throws Exception {
        if (code.startsWith("sh") || code.startsWith("sz")) return fetchSinaCn(code, days, currency);
        if (code.startsWith("hk")) return fetchTencent(code, days, currency);
        return fetchSinaUs(code, days, currency);
    }

    private static String currencyForIndex(String symbol) {
        return switch (symbol) {
            case "000300.SS", "000001.SS" -> "CNY";
            case "^HSI" -> "HKD";
            default -> "USD";
        };
    }

    private static String strip(String symbol) {
        return symbol.substring(0, symbol.length() - 3);
    }

    // ---- 各数据源实现 ----

    /** 新浪 A股日K：返回 [{"day":"2026-08-14","close":"1341.990",...}, ...] */
    private List<DailyClose> fetchSinaCn(String code, int days, String currency) throws Exception {
        String body = get(String.format(SINA_CN_KLINE, code, days), null);
        if (body == null || body.isBlank()) return Collections.emptyList();

        JsonNode arr = objectMapper.readTree(body);
        if (!arr.isArray()) return Collections.emptyList();

        List<DailyClose> result = new ArrayList<>();
        for (JsonNode n : arr) {
            LocalDate date = parseDate(n.path("day").asText(null));
            BigDecimal close = parseClose(n.path("close").asText(null));
            if (date != null && close != null) {
                result.add(build(date, close, currency));
            }
        }
        return result;
    }

    /** 新浪美股日K：JSONP 包裹，形如 {@code var _h=([{"d":"2026-08-14","c":"305.93"},...]);} */
    private List<DailyClose> fetchSinaUs(String symbol, int days, String currency) throws Exception {
        String body = get(String.format(SINA_US_KLINE, symbol),
                "https://stock.finance.sina.com.cn");
        if (body == null) return Collections.emptyList();

        int start = body.indexOf('[');
        int end = body.lastIndexOf(']');
        if (start < 0 || end <= start) return Collections.emptyList();

        JsonNode arr = objectMapper.readTree(body.substring(start, end + 1));
        if (!arr.isArray()) return Collections.emptyList();

        List<DailyClose> all = new ArrayList<>();
        for (JsonNode n : arr) {
            LocalDate date = parseDate(n.path("d").asText(null));
            BigDecimal close = parseClose(n.path("c").asText(null));
            if (date != null && close != null) {
                all.add(build(date, close, currency));
            }
        }
        // 该接口返回全量历史，只取末尾 N 条
        return all.size() <= days ? all : new ArrayList<>(all.subList(all.size() - days, all.size()));
    }

    /** 腾讯港股日K：data.&lt;code&gt;.qfqday = [[日期, 开, 收, 高, 低, 量], ...] */
    private List<DailyClose> fetchTencent(String code, int days, String currency) throws Exception {
        String body = get(String.format(TENCENT_KLINE, code, days), null);
        if (body == null) return Collections.emptyList();

        JsonNode data = objectMapper.readTree(body).path("data").path(code);
        JsonNode rows = data.path("qfqday");
        if (!rows.isArray() || rows.isEmpty()) rows = data.path("day");
        if (!rows.isArray()) return Collections.emptyList();

        List<DailyClose> result = new ArrayList<>();
        for (JsonNode row : rows) {
            if (!row.isArray() || row.size() < 3) continue;
            LocalDate date = parseDate(row.get(0).asText(null));
            BigDecimal close = parseClose(row.get(2).asText(null));
            if (date != null && close != null) {
                result.add(build(date, close, currency));
            }
        }
        return result;
    }

    // ---- 工具 ----

    private String get(String url, String referer) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)")
                .timeout(Duration.ofSeconds(15))
                .GET();
        if (referer != null) b.header("Referer", referer);

        HttpResponse<byte[]> resp = client().send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() != 200) {
            log.warn("History API HTTP {} for {}", resp.statusCode(), url);
            return null;
        }
        return new String(resp.body(), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static DailyClose build(LocalDate date, BigDecimal close, String currency) {
        return DailyClose.builder()
                .date(date)
                .close(close.setScale(6, RoundingMode.HALF_UP))
                .currency(currency)
                .build();
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim().replace('/', '-');
        if (s.length() > 10) s = s.substring(0, 10);
        try {
            return LocalDate.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal parseClose(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            BigDecimal v = new BigDecimal(raw.trim());
            return v.signum() > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
