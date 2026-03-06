package com.homewealth.controller;

import com.homewealth.dto.response.*;
import com.homewealth.security.SecurityUtils;
import com.homewealth.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final SecurityUtils securityUtils;

    @GetMapping("/overview")
    public ApiResponse<DashboardOverviewVO> getOverview() {
        Long userId = securityUtils.getCurrentUserId();
        return ApiResponse.success(dashboardService.getOverview(userId));
    }

    @GetMapping("/sankey")
    public ApiResponse<SankeyDataVO> getSankey() {
        Long userId = securityUtils.getCurrentUserId();
        return ApiResponse.success(dashboardService.getSankeyData(userId));
    }

    @GetMapping("/net-asset/history")
    public ApiResponse<ChartDataVO> getNetAssetHistory(@RequestParam(defaultValue = "90") int days) {
        Long userId = securityUtils.getCurrentUserId();
        return ApiResponse.success(dashboardService.getNetAssetHistory(userId, days));
    }

    @GetMapping("/investment/history")
    public ApiResponse<ChartDataVO> getInvestmentHistory(@RequestParam(defaultValue = "90") int days) {
        Long userId = securityUtils.getCurrentUserId();
        return ApiResponse.success(dashboardService.getInvestmentHistory(userId, days));
    }

    @GetMapping("/holding-rank")
    public ApiResponse<HoldingRankVO> getHoldingRank(@RequestParam(defaultValue = "20") int top) {
        Long userId = securityUtils.getCurrentUserId();
        return ApiResponse.success(dashboardService.getHoldingRank(userId, top));
    }
}
