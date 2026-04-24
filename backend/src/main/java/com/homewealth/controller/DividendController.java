package com.homewealth.controller;

import com.homewealth.dto.response.ApiResponse;
import com.homewealth.dto.response.DividendDetailVO;
import com.homewealth.dto.response.DividendHistoryVO;
import com.homewealth.dto.response.DividendSummaryVO;
import com.homewealth.security.SecurityUtils;
import com.homewealth.service.DividendService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dividend")
@RequiredArgsConstructor
public class DividendController {

    private final DividendService dividendService;
    private final SecurityUtils securityUtils;

    @GetMapping("/summary")
    public ApiResponse<DividendSummaryVO> getSummary() {
        Long userId = securityUtils.getCurrentUserId();
        return ApiResponse.success(dividendService.getDividendSummary(userId));
    }

    @GetMapping("/history")
    public ApiResponse<DividendHistoryVO> getHistory(@RequestParam(defaultValue = "12") int months) {
        Long userId = securityUtils.getCurrentUserId();
        return ApiResponse.success(dividendService.getDividendHistory(userId, months));
    }

    @GetMapping("/detail")
    public ApiResponse<List<DividendDetailVO>> getDetail(
            @RequestParam int year,
            @RequestParam(required = false) Integer month) {
        Long userId = securityUtils.getCurrentUserId();
        return ApiResponse.success(dividendService.getDividendDetail(userId, year, month));
    }

    @PostMapping("/fetch")
    public ApiResponse<Void> fetchDividendEvents() {
        dividendService.fetchAndStoreDividendEvents();
        return ApiResponse.success();
    }

    @PostMapping("/backfill")
    public ApiResponse<Void> backfill(@RequestParam(defaultValue = "3") int months) {
        Long userId = securityUtils.getCurrentUserId();
        dividendService.backfillDividendRecords(userId, months);
        return ApiResponse.success();
    }
}
