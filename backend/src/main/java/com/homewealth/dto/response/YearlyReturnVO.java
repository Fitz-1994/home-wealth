package com.homewealth.dto.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class YearlyReturnVO {
    private String period;            // "2026"
    private BigDecimal returnPct;     // 当年收益率 %（日度链式）
    private BigDecimal absolutePnlCny;// 当年收益额 CNY
    private BigDecimal beginValue;
    private BigDecimal endValue;
    private BigDecimal netCashflow;
}
