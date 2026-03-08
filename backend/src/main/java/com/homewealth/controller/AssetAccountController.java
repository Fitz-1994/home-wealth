package com.homewealth.controller;

import com.homewealth.dto.request.CreateAccountRequest;
import com.homewealth.dto.response.ApiResponse;
import com.homewealth.model.AssetAccount;
import com.homewealth.security.SecurityUtils;
import com.homewealth.service.AssetAccountService;
import com.homewealth.service.DashboardService;
import com.homewealth.service.ExchangeRateService;
import com.homewealth.service.RegularRecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AssetAccountController {

    private final AssetAccountService accountService;
    private final RegularRecordService recordService;
    private final ExchangeRateService exchangeRateService;
    private final DashboardService dashboardService;
    private final SecurityUtils securityUtils;

    @GetMapping
    public ApiResponse<List<AssetAccount>> listAccounts(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String category) {
        Long userId = securityUtils.getCurrentUserId();
        return ApiResponse.success(accountService.listAccounts(userId, type, category));
    }

    @PostMapping
    public ApiResponse<AssetAccount> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        Long userId = securityUtils.getCurrentUserId();
        return ApiResponse.success(accountService.createAccount(userId, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<AssetAccount> getAccount(@PathVariable Long id) {
        Long userId = securityUtils.getCurrentUserId();
        return ApiResponse.success(accountService.getAccount(userId, id));
    }

    @PutMapping("/{id}")
    public ApiResponse<AssetAccount> updateAccount(@PathVariable Long id,
                                                     @RequestBody CreateAccountRequest request) {
        Long userId = securityUtils.getCurrentUserId();
        return ApiResponse.success(accountService.updateAccount(userId, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteAccount(@PathVariable Long id) {
        Long userId = securityUtils.getCurrentUserId();
        accountService.deleteAccount(userId, id);
        return ApiResponse.success();
    }

    @GetMapping("/values")
    public ApiResponse<Map<Long, BigDecimal>> getAccountValues() {
        Long userId = securityUtils.getCurrentUserId();
        return ApiResponse.success(dashboardService.getAccountValues(userId));
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, BigDecimal>> getSummary() {
        Long userId = securityUtils.getCurrentUserId();
        List<AssetAccount> accounts = accountService.listAccounts(userId, null, null);
        // 简单按 category 汇总当前有效 CNY 金额
        Map<String, BigDecimal> summary = new java.util.LinkedHashMap<>();
        summary.put("LIQUID", BigDecimal.ZERO);
        summary.put("FIXED", BigDecimal.ZERO);
        summary.put("RECEIVABLE", BigDecimal.ZERO);
        summary.put("INVESTMENT", BigDecimal.ZERO);
        summary.put("LIABILITY", BigDecimal.ZERO);

        for (AssetAccount account : accounts) {
            if ("REGULAR".equals(account.getAccountType())) {
                var record = recordService.getCurrentRecord(userId, account.getId());
                if (record != null) {
                    summary.merge(account.getAssetCategory(), record.getCnyAmount(), BigDecimal::add);
                }
            }
            // INVESTMENT 账户由 DashboardService 计算，此处不重复
        }
        return ApiResponse.success(summary);
    }
}
