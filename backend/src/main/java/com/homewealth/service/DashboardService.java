package com.homewealth.service;

import com.homewealth.dto.response.*;

public interface DashboardService {
    DashboardOverviewVO getOverview(Long userId);
    SankeyDataVO getSankeyData(Long userId);
    ChartDataVO getNetAssetHistory(Long userId, int days);
    ChartDataVO getInvestmentHistory(Long userId, int days);
    HoldingRankVO getHoldingRank(Long userId, int top);
}
