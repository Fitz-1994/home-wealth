package com.homewealth.service.impl;

import com.homewealth.mapper.ExchangeRateMapper;
import com.homewealth.market.SinaFinanceFetcher;
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
    private final SinaFinanceFetcher sinaFetcher;

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
        log.info("Refreshing exchange rates from Sina Finance...");
        Map<String, BigDecimal> rates = sinaFetcher.fetchFxRates();

        if (rates.isEmpty()) {
            log.error("No exchange rates returned — keeping previous values");
            return;
        }

        for (Map.Entry<String, BigDecimal> entry : rates.entrySet()) {
            ExchangeRate rate = new ExchangeRate();
            rate.setFromCurrency(entry.getKey());
            rate.setToCurrency("CNY");
            rate.setRate(entry.getValue());
            rate.setRateDate(LocalDate.now());
            rate.setSource("SINA");
            exchangeRateMapper.upsert(rate);
            log.info("Updated rate: 1 {} = {} CNY", entry.getKey(), entry.getValue());
        }
    }
}
