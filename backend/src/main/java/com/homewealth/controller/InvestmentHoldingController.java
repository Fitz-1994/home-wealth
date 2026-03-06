package com.homewealth.controller;

import com.homewealth.dto.request.CreateHoldingRequest;
import com.homewealth.dto.response.ApiResponse;
import com.homewealth.dto.response.HoldingWithPriceVO;
import com.homewealth.security.SecurityUtils;
import com.homewealth.service.InvestmentHoldingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/holdings")
@RequiredArgsConstructor
public class InvestmentHoldingController {

    private final InvestmentHoldingService holdingService;
    private final SecurityUtils securityUtils;

    @GetMapping
    public ApiResponse<List<HoldingWithPriceVO>> listHoldings(
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) String market) {
        Long userId = securityUtils.getCurrentUserId();
        return ApiResponse.success(holdingService.listHoldings(userId, accountId, market));
    }

    @GetMapping("/{id}")
    public ApiResponse<HoldingWithPriceVO> getHolding(@PathVariable Long id) {
        Long userId = securityUtils.getCurrentUserId();
        return ApiResponse.success(holdingService.getHolding(userId, id));
    }

    @PostMapping
    public ApiResponse<HoldingWithPriceVO> createHolding(@Valid @RequestBody CreateHoldingRequest request) {
        Long userId = securityUtils.getCurrentUserId();
        return ApiResponse.success(holdingService.createHolding(userId, request));
    }

    @PutMapping("/{id}")
    public ApiResponse<HoldingWithPriceVO> updateHolding(@PathVariable Long id,
                                                          @RequestBody CreateHoldingRequest request) {
        Long userId = securityUtils.getCurrentUserId();
        return ApiResponse.success(holdingService.updateHolding(userId, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> closeHolding(@PathVariable Long id) {
        Long userId = securityUtils.getCurrentUserId();
        holdingService.closeHolding(userId, id);
        return ApiResponse.success();
    }

    @PostMapping("/validate-symbol")
    public ApiResponse<HoldingWithPriceVO> validateSymbol(@RequestBody Map<String, String> body) {
        String symbol = body.get("symbol");
        String priceCurrency = body.getOrDefault("priceCurrency", "CNY");
        return ApiResponse.success(holdingService.validateSymbol(symbol, priceCurrency));
    }
}
