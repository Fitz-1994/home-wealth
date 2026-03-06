package com.homewealth.service;

import com.homewealth.model.MarketPriceCache;

import java.util.List;
import java.util.Map;

public interface MarketDataService {
    MarketPriceCache getLatestPrice(String symbol);
    Map<String, MarketPriceCache> getLatestPrices(List<String> symbols);
    void refreshAllActiveHoldings();
    void refreshSymbols(List<String> symbols);
}
