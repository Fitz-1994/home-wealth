package com.homewealth.controller;

import com.homewealth.dto.request.UpdateCashBalanceRequest;
import com.homewealth.dto.response.ApiResponse;
import com.homewealth.dto.response.CashBalanceVO;
import com.homewealth.security.SecurityUtils;
import com.homewealth.service.InvestmentCashBalanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts/{accountId}/cash-balances")
@RequiredArgsConstructor
public class InvestmentCashBalanceController {

    private final InvestmentCashBalanceService cashBalanceService;
    private final SecurityUtils securityUtils;

    @GetMapping
    public ApiResponse<List<CashBalanceVO>> list(@PathVariable Long accountId) {
        Long userId = securityUtils.getCurrentUserId();
        return ApiResponse.success(cashBalanceService.listByAccount(userId, accountId));
    }

    @PostMapping
    public ApiResponse<CashBalanceVO> upsert(@PathVariable Long accountId,
                                              @Valid @RequestBody UpdateCashBalanceRequest request) {
        Long userId = securityUtils.getCurrentUserId();
        return ApiResponse.success(cashBalanceService.upsert(userId, accountId, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long accountId, @PathVariable Long id) {
        Long userId = securityUtils.getCurrentUserId();
        cashBalanceService.delete(userId, accountId, id);
        return ApiResponse.success();
    }
}
