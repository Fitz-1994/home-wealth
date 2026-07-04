package com.homewealth.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class DailyReturnVO {
    private LocalDate date;
    private BigDecimal returnPct;     // 当日收益率 %（Modified Dietz）
    private BigDecimal beginValue;    // 期初市值 CNY
    private BigDecimal endValue;      // 期末市值 CNY
    private BigDecimal netCashflow;   // 当日净入金 CNY
    private BigDecimal pnlCny;        // 当日收益额 CNY
}
