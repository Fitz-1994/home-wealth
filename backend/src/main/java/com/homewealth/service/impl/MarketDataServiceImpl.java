package com.homewealth.service.impl;

import com.homewealth.mapper.InvestmentHoldingMapper;
import com.homewealth.mapper.MarketPriceCacheMapper;
import com.homewealth.market.MarketQuote;
import com.homewealth.market.YahooFinanceFetcher;
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
        log.info("Refreshing market prices for {} symbols", symbols.size());
        Map<String, MarketQuote> quotes = yahooFetcher.fetchQuotes(symbols);

        for (String symbol : symbols) {
            MarketQuote quote = quotes.get(symbol);
            if (quote != null) {
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
    }

    private String inferMarket(String symbol) {
        if (symbol.endsWith("=X")) return "FX";
        if (symbol.endsWith(".SS") || symbol.endsWith(".SZ")) return "CN_A";
        if (symbol.endsWith(".HK") && symbol.contains("C") || symbol.endsWith(".HK") && symbol.contains("P")) return "HK_OPT";
        if (symbol.endsWith(".HK")) return "HK";
        // 美股期权：符合 OCC 格式（字母+6位日期+C/P+8位价格）
        if (symbol.matches("[A-Z]+\\d{6}[CP]\\d{8}")) return "US_OPT";
        return "US";
    }
}
