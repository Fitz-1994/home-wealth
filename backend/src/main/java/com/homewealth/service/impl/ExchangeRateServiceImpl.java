package com.homewealth.service.impl;

import com.homewealth.mapper.ExchangeRateMapper;
import com.homewealth.market.MarketQuote;
import com.homewealth.market.YahooFinanceFetcher;
import com.homewealth.model.ExchangeRate;
import com.homewealth.service.ExchangeRateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateServiceImpl implements ExchangeRateService {

    private final ExchangeRateMapper exchangeRateMapper;
    private final YahooFinanceFetcher yahooFetcher;

    // Yahoo Finance 汇率 symbol 格式: USDCNY=X（1 USD = ? CNY）
    private static final Map<String, String> FX_SYMBOLS = Map.of(
            "USD", "USDCNY=X",
            "HKD", "HKDCNY=X",
            "EUR", "EURCNY=X",
            "JPY", "JPYCNY=X",
            "GBP", "GBPCNY=X"
    );

    @Override
    public BigDecimal getRate(String fromCurrency, String toCurrency) {
        if (fromCurrency.equals(toCurrency)) return BigDecimal.ONE;
        ExchangeRate rate = exchangeRateMapper.findLatest(fromCurrency, toCurrency);
        if (rate != null) return rate.getRate();
        log.warn("No exchange rate found for {}->{}, using 1.0", fromCurrency, toCurrency);
        return BigDecimal.ONE;
    }

    @Override
    public BigDecimal toCny(BigDecimal amount, String currency) {
        if (amount == null) return BigDecimal.ZERO;
        if ("CNY".equals(currency)) return amount;
        BigDecimal rate = getRate(currency, "CNY");
        return amount.multiply(rate).setScale(4, RoundingMode.HALF_UP);
    }

    @Override
    public Map<String, BigDecimal> getAllRatesToCny() {
        Map<String, BigDecimal> result = new HashMap<>();
        result.put("CNY", BigDecimal.ONE);
        List<ExchangeRate> rates = exchangeRateMapper.findAllLatest();
        for (ExchangeRate rate : rates) {
            if ("CNY".equals(rate.getToCurrency())) {
                result.put(rate.getFromCurrency(), rate.getRate());
            }
        }
        return result;
    }

    @Override
    public void refreshRates() {
        log.info("Refreshing exchange rates from Yahoo Finance...");
        List<String> symbols = new ArrayList<>(FX_SYMBOLS.values());
        Map<String, MarketQuote> quotes = yahooFetcher.fetchQuotes(symbols);

        for (Map.Entry<String, String> entry : FX_SYMBOLS.entrySet()) {
            String currency = entry.getKey();
            String symbol = entry.getValue();
            MarketQuote quote = quotes.get(symbol);
            if (quote != null && quote.getPrice().compareTo(BigDecimal.ZERO) > 0) {
                ExchangeRate rate = new ExchangeRate();
                rate.setFromCurrency(currency);
                rate.setToCurrency("CNY");
                rate.setRate(quote.getPrice());
                rate.setRateDate(LocalDate.now());
                rate.setSource("YAHOO");
                exchangeRateMapper.upsert(rate);
                log.info("Updated rate: 1 {} = {} CNY", currency, quote.getPrice());
            } else {
                log.warn("Failed to get rate for {}", symbol);
            }
        }
    }
}
