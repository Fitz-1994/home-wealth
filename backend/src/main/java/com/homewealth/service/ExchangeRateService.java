package com.homewealth.service;

import java.math.BigDecimal;
import java.util.Map;

public interface ExchangeRateService {
    BigDecimal getRate(String fromCurrency, String toCurrency);
    BigDecimal toCny(BigDecimal amount, String currency);
    Map<String, BigDecimal> getAllRatesToCny();
    void refreshRates();
}
