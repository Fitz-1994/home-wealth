package com.homewealth.controller;

import com.homewealth.dto.response.ApiResponse;
import com.homewealth.model.MarketPriceCache;
import com.homewealth.security.SecurityUtils;
import com.homewealth.service.ExchangeRateService;
import com.homewealth.service.MarketDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class MarketDataController {

    private final MarketDataService marketDataService;
    private final ExchangeRateService exchangeRateService;
    private final SecurityUtils securityUtils;

    @GetMapping("/prices")
    public ApiResponse<Map<String, MarketPriceCache>> getPrices(@RequestParam String symbols) {
        List<String> symbolList = Arrays.asList(symbols.split(","));
        return ApiResponse.success(marketDataService.getLatestPrices(symbolList));
    }

    @PostMapping("/refresh")
    public ApiResponse<Void> refreshMyHoldings() {
        marketDataService.refreshAllActiveHoldings();
        return ApiResponse.success();
    }

    @GetMapping("/rates")
    public ApiResponse<Map<String, BigDecimal>> getRates() {
        return ApiResponse.success(exchangeRateService.getAllRatesToCny());
    }

    @GetMapping("/rates/{currency}")
    public ApiResponse<BigDecimal> getRate(@PathVariable String currency) {
        return ApiResponse.success(exchangeRateService.getRate(currency.toUpperCase(), "CNY"));
    }
}
