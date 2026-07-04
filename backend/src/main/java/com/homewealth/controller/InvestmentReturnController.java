package com.homewealth.controller;

import com.homewealth.dto.response.ApiResponse;
import com.homewealth.dto.response.DailyReturnVO;
import com.homewealth.dto.response.MonthlyReturnVO;
import com.homewealth.dto.response.ReturnSummaryVO;
import com.homewealth.dto.response.YearlyReturnVO;
import com.homewealth.security.SecurityUtils;
import com.homewealth.service.InvestmentReturnService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/returns")
@RequiredArgsConstructor
public class InvestmentReturnController {

    private final InvestmentReturnService returnService;
    private final SecurityUtils securityUtils;

    @GetMapping("/daily")
    public ApiResponse<List<DailyReturnVO>> daily(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Long userId = securityUtils.getCurrentUserId();
        return ApiResponse.success(returnService.getDailyReturns(userId, from, to));
    }

    @GetMapping("/monthly")
    public ApiResponse<List<MonthlyReturnVO>> monthly(@RequestParam(required = false) Integer year) {
        Long userId = securityUtils.getCurrentUserId();
        return ApiResponse.success(returnService.getMonthlyReturns(userId, year));
    }

    @GetMapping("/yearly")
    public ApiResponse<List<YearlyReturnVO>> yearly() {
        Long userId = securityUtils.getCurrentUserId();
        return ApiResponse.success(returnService.getYearlyReturns(userId));
    }

    @GetMapping("/summary")
    public ApiResponse<ReturnSummaryVO> summary() {
        Long userId = securityUtils.getCurrentUserId();
        return ApiResponse.success(returnService.getReturnSummary(userId));
    }
}
