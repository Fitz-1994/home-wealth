package com.homewealth.dto.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class BenchmarkReturnVO {
    private String period;        // "2026-05" 或 "2026"
    private BigDecimal returnPct;  // 该周期指数收益率 %
}
