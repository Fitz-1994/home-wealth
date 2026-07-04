package com.homewealth.controller;

import com.homewealth.dto.request.CreateTransactionRequest;
import com.homewealth.dto.response.ApiResponse;
import com.homewealth.dto.response.TransactionVO;
import com.homewealth.security.SecurityUtils;
import com.homewealth.service.InvestmentTransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class InvestmentTransactionController {

    private final InvestmentTransactionService txnService;
    private final SecurityUtils securityUtils;

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String txnType,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) Long holdingId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Long userId = securityUtils.getCurrentUserId();
        return ApiResponse.success(txnService.page(userId, from, to, txnType, accountId, holdingId, page, size));
    }

    @PostMapping
    public ApiResponse<TransactionVO> create(@Valid @RequestBody CreateTransactionRequest request) {
        Long userId = securityUtils.getCurrentUserId();
        return ApiResponse.success(txnService.create(userId, request));
    }

    @PutMapping("/{id}")
    public ApiResponse<TransactionVO> update(@PathVariable Long id,
                                              @Valid @RequestBody CreateTransactionRequest request) {
        Long userId = securityUtils.getCurrentUserId();
        return ApiResponse.success(txnService.update(userId, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        Long userId = securityUtils.getCurrentUserId();
        txnService.delete(userId, id);
        return ApiResponse.success();
    }

    /**
     * 为当前用户的现有持仓生成 OPENING 合成交易（已存在则跳过）。
     * 上线后跑一次即可。
     */
    @PostMapping("/generate-opening")
    public ApiResponse<Map<String, Object>> generateOpening() {
        Long userId = securityUtils.getCurrentUserId();
        int count = txnService.generateOpeningTransactions(userId);
        Map<String, Object> body = new HashMap<>();
        body.put("generated", count);
        return ApiResponse.success(body);
    }
}
