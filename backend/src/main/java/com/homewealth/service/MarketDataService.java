package com.homewealth.service;

import com.homewealth.model.MarketPriceCache;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface MarketDataService {
    MarketPriceCache getLatestPrice(String symbol);
    Map<String, MarketPriceCache> getLatestPrices(List<String> symbols);
    void refreshAllActiveHoldings();
    void refreshSymbols(List<String> symbols);

    // 录入手工价：写入 market_price_cache(source=MANUAL, trade_date=今日)
    // 后续 refresh 会跳过该 symbol，直到 deleteManualPrice 清除
    MarketPriceCache upsertManualPrice(String symbol, BigDecimal price, String currency);

    // 清除手工价，下次刷新恢复自动数据源
    void deleteManualPrice(String symbol);
}
