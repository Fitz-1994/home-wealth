package com.homewealth.market;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 新浪财经行情适配器（仅用于获取A股/港股中文名称）
 *
 * 接口：https://hq.sinajs.cn/list=sh600519,sz000858,hk00700
 * 响应：var hq_str_sh600519="贵州茅台股份有限公司,1488.00,...";
 * 第一个逗号分隔字段即为中文公司名称。
 */
@Slf4j
@Component
public class SinaFinanceFetcher {

    private static final String SINA_URL = "https://hq.sinajs.cn/list=";
    private static final Pattern LINE_PATTERN =
            Pattern.compile("var hq_str_(\\w+)=\"([^\"]*?)\"");

    private final HttpClient httpClient;

    public SinaFinanceFetcher() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * 批量获取中文名称。
     *
     * @param symbols Yahoo Finance 格式的标的列表（600519.SS / 0700.HK）
     * @return symbol → 中文名称 的映射（仅包含成功获取的条目）
     */
    public Map<String, String> fetchChineseNames(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) return Collections.emptyMap();

        // 转换成新浪格式，记录对应关系
        Map<String, String> sinaToYahoo = new LinkedHashMap<>();
        for (String sym : symbols) {
            String sinaCode = toSinaCode(sym);
            if (sinaCode != null) {
                sinaToYahoo.put(sinaCode, sym);
            }
        }
        if (sinaToYahoo.isEmpty()) return Collections.emptyMap();

        String queryList = String.join(",", sinaToYahoo.keySet());
        String url = SINA_URL + queryList;

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)")
                    .header("Referer", "https://finance.sina.com.cn")
                    .header("Accept-Charset", "GBK,utf-8;q=0.7,*;q=0.3")
                    .timeout(Duration.ofSeconds(8))
                    .GET().build();

            HttpResponse<byte[]> response = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                log.warn("Sina Finance HTTP {}", response.statusCode());
                return Collections.emptyMap();
            }

            // 新浪接口返回 GBK 编码
            String body = new String(response.body(), "GBK");
            return parseResponse(body, sinaToYahoo);

        } catch (Exception e) {
            log.warn("Sina Finance fetch failed: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 解析新浪响应，提取 symbol → 中文名称
     */
    private Map<String, String> parseResponse(String body, Map<String, String> sinaToYahoo) {
        Map<String, String> result = new HashMap<>();
        Matcher m = LINE_PATTERN.matcher(body);
        while (m.find()) {
            String sinaCode = m.group(1);
            String fields = m.group(2);
            if (fields == null || fields.isEmpty()) continue;

            String chineseName = fields.split(",")[0].trim();
            if (chineseName.isEmpty()) continue;

            String yahooSymbol = sinaToYahoo.get(sinaCode);
            if (yahooSymbol != null) {
                result.put(yahooSymbol, chineseName);
                log.debug("Sina name resolved: {} → {}", yahooSymbol, chineseName);
            }
        }
        return result;
    }

    /**
     * 将 Yahoo Finance symbol 转换为新浪代码：
     *   600519.SS  → sh600519
     *   000858.SZ  → sz000858
     *   0700.HK    → hk00700（港股5位，不足补前导零）
     *
     * @return 新浪代码，若无法转换则返回 null
     */
    private String toSinaCode(String symbol) {
        if (symbol == null) return null;
        if (symbol.endsWith(".SS")) {
            return "sh" + symbol.substring(0, symbol.length() - 3);
        }
        if (symbol.endsWith(".SZ")) {
            return "sz" + symbol.substring(0, symbol.length() - 3);
        }
        if (symbol.endsWith(".HK")) {
            String code = symbol.substring(0, symbol.length() - 3);
            // 港股代码需要5位（前补0）
            while (code.length() < 5) code = "0" + code;
            return "hk" + code;
        }
        return null; // 美股/期权/汇率无需中文名
    }
}
