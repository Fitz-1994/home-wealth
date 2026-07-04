package com.homewealth.dto.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MonthlyReturnVO {
    private String period;            // "2026-05"
    private BigDecimal returnPct;     // 当月收益率 %（日度链式）
    private BigDecimal absolutePnlCny;// 当月收益额 CNY
    private BigDecimal beginValue;    // 期初市值 CNY
    private BigDecimal endValue;      // 期末市值 CNY
    private BigDecimal netCashflow;   // 当月净入金 CNY
}
