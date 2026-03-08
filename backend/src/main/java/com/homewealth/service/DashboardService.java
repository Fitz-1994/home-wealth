package com.homewealth.service;

import com.homewealth.dto.response.*;

import java.math.BigDecimal;
import java.util.Map;

public interface DashboardService {
    DashboardOverviewVO getOverview(Long userId);
    SankeyDataVO getSankeyData(Long userId);
    ChartDataVO getNetAssetHistory(Long userId, int days);
    ChartDataVO getInvestmentHistory(Long userId, int days);
    HoldingRankVO getHoldingRank(Long userId, int top);
    /** 返回每个账户的 CNY 估值，key = accountId */
    Map<Long, BigDecimal> getAccountValues(Long userId);
}
