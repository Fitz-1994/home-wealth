package com.homewealth.model;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class DailyInvestmentSnapshot {
    private Long id;
    private Long userId;
    private LocalDate snapshotDate;
    private BigDecimal totalValueCny;
    private BigDecimal totalCostCny;
    private BigDecimal unrealizedPnl;
    private BigDecimal cnAValueCny;
    private BigDecimal hkValueCny;
    private BigDecimal usValueCny;
    private BigDecimal hkOptValueCny;
    private BigDecimal usOptValueCny;
    private BigDecimal otherValueCny;
    private LocalDateTime createdAt;
}
