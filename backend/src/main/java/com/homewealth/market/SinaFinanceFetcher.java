package com.homewealth.market;

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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 新浪财经行情适配器 —— A股 / 港股 / 美股 / 汇率 / 指数。
 *
 * <p>接口：https://hq.sinajs.cn/list=sh600519,hk00700,gb_aapl,fx_susdcny
 * 一次请求可批量返回多个标的，响应为 GBK 编码的 JS 变量赋值。
 *
 * <p>相比按 symbol 并发单独请求（Yahoo 旧实现），批量请求把 N 次调用压成 1 次，
 * 既快又不会触发数据源限流 —— 后者正是 Yahoo 封禁本机 IP 的直接原因。
 *
 * <p>不支持期权（港股个股期权 / 美股期权），这些标的需由券商 API 提供。
 */
@Slf4j
@Component
public class SinaFinanceFetcher {

    private static final String SINA_URL = "https://hq.sinajs.cn/list=";
    private static final Pattern LINE_PATTERN =
            Pattern.compile("var hq_str_(\\w+)=\"([^\"]*?)\"");
    private static final Pattern DATE_PATTERN =
            Pattern.compile("(\\d{4})[-/](\\d{2})[-/](\\d{2})");
    /** 美股收盘时刻字段，形如 "Aug 14 04:00PM EDT" */
    private static final Pattern US_CLOSE_PATTERN =
            Pattern.compile("^([A-Za-z]{3})\\s+(\\d{1,2})\\b");
    private static final DateTimeFormatter US_CLOSE_FMT =
            DateTimeFormatter.ofPattern("MMM d yyyy", Locale.ENGLISH);

    /** 单次请求的标的数上限，超出则自动分批 */
    private static final int BATCH_SIZE = 50;

    private static final ZoneId SH = ZoneId.of("Asia/Shanghai");

    /** 汇率：币种 -> 新浪外汇代码（均为 1 外币 = ? CNY） */
    private static final Map<String, String> FX_CODES = new LinkedHashMap<>() {{
        put("USD", "fx_susdcny");
        put("HKD", "fx_shkdcny");
        put("EUR", "fx_seurcny");
        put("GBP", "fx_sgbpcny");
        put("JPY", "fx_sjpycny");
    }};

    private final HttpClient httpClient;

    public SinaFinanceFetcher() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    // ---- 行情 ----

    /**
     * 批量获取行情（A股 / 港股 / 美股）。
     *
     * @param symbols Yahoo Finance 格式的标的列表
     * @return symbol → 行情，仅包含成功解析的条目
     */
    public Map<String, MarketQuote> fetchQuotes(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) return Collections.emptyMap();

        Map<String, String> codeToSymbol = new LinkedHashMap<>();
        for (String sym : symbols) {
            String code = toSinaCode(sym);
            if (code != null) {
                codeToSymbol.put(code, sym);
            } else {
                log.debug("Sina does not support symbol: {}", sym);
            }
        }
        if (codeToSymbol.isEmpty()) return Collections.emptyMap();

        Map<String, MarketQuote> result = new HashMap<>();
        for (List<String> chunk : partition(new ArrayList<>(codeToSymbol.keySet()), BATCH_SIZE)) {
            String body = request(chunk);
            if (body == null) continue;

            Matcher m = LINE_PATTERN.matcher(body);
            while (m.find()) {
                String code = m.group(1);
                String payload = m.group(2);
                String symbol = codeToSymbol.get(code);
                if (symbol == null || payload.isEmpty()) continue;

                parseQuote(symbol, code, payload).ifPresent(q -> result.put(symbol, q));
            }
        }

        int missing = codeToSymbol.size() - result.size();
        if (missing > 0) {
            log.warn("Sina returned no data for {} of {} symbols", missing, codeToSymbol.size());
        }
        return result;
    }

    /**
     * 批量获取中文名称（保留给仅需名称的调用方）。
     */
    public Map<String, String> fetchChineseNames(List<String> symbols) {
        Map<String, String> names = new HashMap<>();
        fetchQuotes(symbols).forEach((sym, q) -> {
            if (q.getSymbolName() != null && !q.getSymbolName().isEmpty()) {
                names.put(sym, q.getSymbolName());
            }
        });
        return names;
    }

    // ---- 汇率 ----

    /**
     * 获取各币种对人民币汇率。
     *
     * @return 币种（USD/HKD/...） → 1 外币折合人民币
     */
    public Map<String, BigDecimal> fetchFxRates() {
        String body = request(new ArrayList<>(FX_CODES.values()));
        if (body == null) return Collections.emptyMap();

        Map<String, String> codeToCurrency = new HashMap<>();
        FX_CODES.forEach((ccy, code) -> codeToCurrency.put(code, ccy));

        Map<String, BigDecimal> result = new HashMap<>();
        Matcher m = LINE_PATTERN.matcher(body);
        while (m.find()) {
            String currency = codeToCurrency.get(m.group(1));
            String payload = m.group(2);
            if (currency == null || payload.isEmpty()) continue;

            String[] f = payload.split(",");
            // 新浪外汇字段：[0]=时间 [1]=昨收 [2]=今开 ... [8]=最新价 [9]=名称
            BigDecimal rate = parseDecimal(f, 8);
            if (rate != null && rate.signum() > 0) {
                result.put(currency, rate);
            } else {
                log.warn("Sina FX parse failed for {}: {}", currency, payload);
            }
        }
        return result;
    }

    // ---- 内部实现 ----

    private String request(List<String> codes) {
        String url = SINA_URL + String.join(",", codes);
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)")
                    .header("Referer", "https://finance.sina.com.cn")
                    .header("Accept-Charset", "GBK,utf-8;q=0.7,*;q=0.3")
                    .timeout(Duration.ofSeconds(10))
                    .GET().build();

            HttpResponse<byte[]> response = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                log.warn("Sina Finance HTTP {} for {} codes", response.statusCode(), codes.size());
                return null;
            }
            // 新浪接口返回 GBK 编码
            return new String(response.body(), "GBK");
        } catch (Exception e) {
            log.warn("Sina Finance fetch failed ({} codes): {}", codes.size(), e.getMessage());
            return null;
        }
    }

    /**
     * 解析单条行情。各市场字段布局不同：
     * <ul>
     *   <li>A股 sh/sz：[0]=名称 [2]=昨收 [3]=现价</li>
     *   <li>港股 hk：[1]=中文名 [6]=现价 [8]=涨跌幅%</li>
     *   <li>美股 gb_：[0]=中文名 [1]=现价 [2]=涨跌幅%</li>
     * </ul>
     */
    private Optional<MarketQuote> parseQuote(String symbol, String code, String payload) {
        String[] f = payload.split(",");
        String name;
        BigDecimal price;
        BigDecimal changePct;
        String currency;
        LocalDate tradeDate;

        try {
            if (code.startsWith("sh") || code.startsWith("sz")) {
                if (f.length < 4) return Optional.empty();
                name = f[0].trim();
                price = parseDecimal(f, 3);
                BigDecimal prevClose = parseDecimal(f, 2);
                changePct = pctChange(price, prevClose);
                currency = "CNY";
                tradeDate = extractTradeDate(payload);
            } else if (code.startsWith("hk")) {
                if (f.length < 9) return Optional.empty();
                name = f[1].trim().isEmpty() ? f[0].trim() : f[1].trim();
                price = parseDecimal(f, 6);
                changePct = parseDecimal(f, 8);
                currency = "HKD";
                tradeDate = extractTradeDate(payload);
            } else if (code.startsWith("gb_")) {
                if (f.length < 3) return Optional.empty();
                name = f[0].trim();
                price = parseDecimal(f, 1);
                changePct = parseDecimal(f, 2);
                currency = "USD";
                tradeDate = parseUsTradeDate(f);
            } else {
                return Optional.empty();
            }
        } catch (Exception e) {
            log.warn("Sina parse error for {}: {}", symbol, e.getMessage());
            return Optional.empty();
        }

        if (price == null || price.signum() <= 0) {
            log.warn("Sina zero/invalid price for {}", symbol);
            return Optional.empty();
        }

        return Optional.of(MarketQuote.builder()
                .symbol(symbol)
                .symbolName(name.isEmpty() ? symbol : name)
                .price(price)
                .currency(currency)
                .changePct(changePct != null ? changePct.setScale(4, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                .tradeDate(tradeDate != null ? tradeDate : LocalDate.now(SH))
                .source("SINA")
                .build());
    }

    /**
     * 美股交易日：取字段 25 的收盘时刻（形如 {@code Aug 14 04:00PM EDT}）配字段 29 的年份。
     *
     * <p>不能用字段 3 的时间戳 —— 那是北京时间，美股收盘（16:00 EDT）对应北京次日凌晨，
     * 直接取其日期会把交易日整体记晚一天。
     */
    private static LocalDate parseUsTradeDate(String[] f) {
        if (f.length <= 29) return null;
        Matcher m = US_CLOSE_PATTERN.matcher(f[25].trim());
        if (!m.find()) return null;
        try {
            String year = f[29].trim();
            if (!year.matches("\\d{4}")) return null;
            return LocalDate.parse(m.group(1) + " " + m.group(2) + " " + year, US_CLOSE_FMT);
        } catch (Exception e) {
            return null;
        }
    }

    /** 从报文中提取交易日（支持 2026-08-14 与 2026/08/14 两种写法），缺失时回退当日 */
    private LocalDate extractTradeDate(String payload) {
        Matcher m = DATE_PATTERN.matcher(payload);
        LocalDate found = null;
        while (m.find()) {
            try {
                found = LocalDate.parse(
                        m.group(1) + "-" + m.group(2) + "-" + m.group(3),
                        DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (Exception ignored) {
                // 继续找下一个候选
            }
        }
        return found != null ? found : LocalDate.now(SH);
    }

    private static BigDecimal pctChange(BigDecimal price, BigDecimal prevClose) {
        if (price == null || prevClose == null || prevClose.signum() == 0) return BigDecimal.ZERO;
        return price.subtract(prevClose)
                .divide(prevClose, 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    private static BigDecimal parseDecimal(String[] fields, int idx) {
        if (idx >= fields.length) return null;
        String raw = fields[idx].trim();
        if (raw.isEmpty()) return null;
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> chunks = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            chunks.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return chunks;
    }

    /**
     * 将 Yahoo Finance symbol 转换为新浪代码：
     * <pre>
     *   600519.SS  → sh600519
     *   000858.SZ  → sz000858
     *   0700.HK    → hk00700（港股5位，不足补前导零）
     *   AAPL       → gb_aapl
     * </pre>
     *
     * @return 新浪代码；期权、汇率等不支持的标的返回 null
     */
    private String toSinaCode(String symbol) {
        if (symbol == null || symbol.isBlank()) return null;

        // 期权不受支持：美股 OCC 格式
        if (symbol.matches("[A-Z]+\\d{6}[CP]\\d{8}")) return null;
        if (symbol.endsWith("=X")) return null;

        if (symbol.endsWith(".SS")) {
            return "sh" + symbol.substring(0, symbol.length() - 3);
        }
        if (symbol.endsWith(".SZ")) {
            return "sz" + symbol.substring(0, symbol.length() - 3);
        }
        if (symbol.endsWith(".HK")) {
            String code = symbol.substring(0, symbol.length() - 3);
            // 仅纯数字为正股；含字母的是期权（如 TCH261230P400.HK），无免费源
            if (!code.matches("\\d+")) return null;
            // 港股代码需要5位（前补0）
            while (code.length() < 5) code = "0" + code;
            return "hk" + code;
        }
        // 场外基金（6位纯数字）由 ChinaFundFetcher 处理
        if (symbol.matches("\\d{6}")) return null;
        // 其余按美股处理
        if (symbol.matches("[A-Za-z.\\-]+")) {
            return "gb_" + symbol.toLowerCase(Locale.ROOT);
        }
        return null;
    }
}
