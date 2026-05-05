package com.homewealth.service.impl;

import com.homewealth.exception.BusinessException;
import com.homewealth.exception.ErrorCode;
import com.homewealth.mapper.InvestmentHoldingMapper;
import com.homewealth.mapper.MarketPriceCacheMapper;
import com.homewealth.market.ChinaFundFetcher;
import com.homewealth.market.MarketQuote;
import com.homewealth.market.SinaFinanceFetcher;
import com.homewealth.market.YahooFinanceFetcher;
import com.homewealth.model.InvestmentHolding;
import com.homewealth.model.MarketPriceCache;
import com.homewealth.service.ExchangeRateService;
import com.homewealth.service.MarketDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataServiceImpl implements MarketDataService {

    private final MarketPriceCacheMapper priceCacheMapper;
    private final InvestmentHoldingMapper holdingMapper;
    private final YahooFinanceFetcher yahooFetcher;
    private final SinaFinanceFetcher sinaFetcher;
    private final ChinaFundFetcher fundFetcher;
    private final ExchangeRateService exchangeRateService;

    @Override
    public MarketPriceCache getLatestPrice(String symbol) {
        return priceCacheMapper.findLatestBySymbol(symbol);
    }

    @Override
    public Map<String, MarketPriceCache> getLatestPrices(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) return Collections.emptyMap();
        List<MarketPriceCache> caches = priceCacheMapper.findLatestBySymbols(symbols);
        Map<String, MarketPriceCache> result = new HashMap<>();
        for (MarketPriceCache cache : caches) {
            result.put(cache.getSymbol(), cache);
        }
        return result;
    }

    @Override
    public void refreshAllActiveHoldings() {
        List<String> symbols = holdingMapper.findAllActiveSymbols();
        if (!symbols.isEmpty()) {
            refreshSymbols(symbols);
        }
    }

    @Override
    public void refreshSymbols(List<String> symbols) {
        // 跳过最新行为手工价的 symbol —— 用户已显式覆盖，不应被自动抓取覆盖
        Set<String> manual = new HashSet<>(priceCacheMapper.findManualSymbols(symbols));
        List<String> targets = symbols.stream().filter(s -> !manual.contains(s)).toList();
        if (!manual.isEmpty()) {
            log.info("Skipping {} manual-priced symbols: {}", manual.size(), manual);
        }
        if (targets.isEmpty()) {
            log.info("No symbols to refresh after filtering manual ones");
            return;
        }

        log.info("Refreshing market prices for {} symbols", targets.size());

        // 分离公募基金和其他标的
        List<String> fundSymbols = targets.stream().filter(this::isFundSymbol).toList();
        List<String> yahooSymbols = targets.stream().filter(s -> !isFundSymbol(s)).toList();

        // 获取 Yahoo 行情
        Map<String, MarketQuote> quotes = new HashMap<>();
        if (!yahooSymbols.isEmpty()) {
            quotes.putAll(yahooFetcher.fetchQuotes(yahooSymbols));
        }

        // 获取公募基金净值
        if (!fundSymbols.isEmpty()) {
            quotes.putAll(fundFetcher.fetchQuotes(fundSymbols));
        }

        // 对 A股/港股 额外获取中文名称
        List<String> cnHkSymbols = yahooSymbols.stream()
                .filter(s -> s.endsWith(".SS") || s.endsWith(".SZ") || s.endsWith(".HK"))
                .toList();
        Map<String, String> chineseNames = cnHkSymbols.isEmpty()
                ? Collections.emptyMap()
                : sinaFetcher.fetchChineseNames(cnHkSymbols);

        for (String symbol : targets) {
            MarketQuote quote = quotes.get(symbol);
            if (quote != null) {
                // 优先使用新浪获取的中文名称（A股/港股）
                String chineseName = chineseNames.get(symbol);
                if (chineseName != null && !chineseName.isEmpty()) {
                    quote.setSymbolName(chineseName);
                }
                savePriceCache(symbol, quote);
            } else {
                log.warn("No quote returned for symbol: {}, marking stale", symbol);
                priceCacheMapper.markStale(symbol);
            }
        }
    }

    private void savePriceCache(String symbol, MarketQuote quote) {
        BigDecimal cnyRate = exchangeRateService.getRate(quote.getCurrency(), "CNY");
        BigDecimal cnyPrice = quote.getPrice().multiply(cnyRate);

        MarketPriceCache cache = new MarketPriceCache();
        cache.setSymbol(symbol);
        cache.setSymbolName(quote.getSymbolName());
        cache.setPrice(quote.getPrice());
        cache.setCurrency(quote.getCurrency());
        cache.setCnyRate(cnyRate);
        cache.setCnyPrice(cnyPrice);
        cache.setChangePct(quote.getChangePct());
        cache.setTradeDate(quote.getTradeDate() != null ? quote.getTradeDate() : LocalDate.now());
        cache.setSource(quote.getSource());
        cache.setIsStale(false);

        // market 字段从 symbol 推断
        cache.setMarket(inferMarket(symbol));

        priceCacheMapper.upsert(cache);

        // 将标的名称同步回持仓表（使 Holdings 列表也显示正确名称）
        if (quote.getSymbolName() != null && !quote.getSymbolName().isEmpty()) {
            holdingMapper.updateSymbolName(symbol, quote.getSymbolName());
        }
    }

    @Override
    public MarketPriceCache upsertManualPrice(String symbol, BigDecimal price, String currency) {
        if (symbol == null || symbol.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "symbol 不能为空");
        }
        if (price == null || price.signum() <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "price 必须 > 0");
        }
        symbol = symbol.trim();

        // 推断市场 + 找已有元信息（symbol_name / 默认币种）
        String market = inferMarket(symbol);
        InvestmentHolding holding = holdingMapper.findFirstActiveBySymbol(symbol);
        MarketPriceCache existing = priceCacheMapper.findLatestBySymbol(symbol);

        String resolvedCurrency = (currency != null && !currency.isBlank())
                ? currency.toUpperCase()
                : (holding != null && holding.getPriceCurrency() != null ? holding.getPriceCurrency()
                        : (existing != null ? existing.getCurrency() : defaultCurrencyForMarket(market)));

        String symbolName = (holding != null && holding.getSymbolName() != null && !holding.getSymbolName().isBlank())
                ? holding.getSymbolName()
                : (existing != null ? existing.getSymbolName() : symbol);

        BigDecimal cnyRate = exchangeRateService.getRate(resolvedCurrency, "CNY");
        BigDecimal cnyPrice = price.multiply(cnyRate);

        MarketPriceCache cache = new MarketPriceCache();
        cache.setSymbol(symbol);
        cache.setSymbolName(symbolName);
        cache.setMarket(market);
        cache.setPrice(price);
        cache.setCurrency(resolvedCurrency);
        cache.setCnyRate(cnyRate);
        cache.setCnyPrice(cnyPrice);
        cache.setChangePct(BigDecimal.ZERO);
        cache.setTradeDate(LocalDate.now());
        cache.setSource("MANUAL");
        cache.setIsStale(false);

        priceCacheMapper.upsert(cache);
        log.info("Manual price upserted: {} = {} {} (CNY rate {})", symbol, price, resolvedCurrency, cnyRate);
        return cache;
    }

    @Override
    public void deleteManualPrice(String symbol) {
        if (symbol == null || symbol.isBlank()) return;
        int affected = priceCacheMapper.deleteManualBySymbol(symbol.trim());
        log.info("Cleared {} manual price rows for symbol {}", affected, symbol);
    }

    private String defaultCurrencyForMarket(String market) {
        return switch (market) {
            case "CN_A", "CN_FUND" -> "CNY";
            case "HK", "HK_OPT" -> "HKD";
            case "FX" -> "CNY";
            default -> "USD";
        };
    }

    private boolean isFundSymbol(String symbol) {
        return symbol != null && symbol.matches("\\d{6}");
    }

    private String inferMarket(String symbol) {
        if (isFundSymbol(symbol)) return "CN_FUND";
        if (symbol.endsWith("=X")) return "FX";
        if (symbol.endsWith(".SS") || symbol.endsWith(".SZ")) return "CN_A";
        if (symbol.endsWith(".HK") && symbol.contains("C") || symbol.endsWith(".HK") && symbol.contains("P")) return "HK_OPT";
        if (symbol.endsWith(".HK")) return "HK";
        // 美股期权：符合 OCC 格式（字母+6位日期+C/P+8位价格）
        if (symbol.matches("[A-Z]+\\d{6}[CP]\\d{8}")) return "US_OPT";
        return "US";
    }
}
