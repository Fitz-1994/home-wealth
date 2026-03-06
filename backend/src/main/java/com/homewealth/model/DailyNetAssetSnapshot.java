package com.homewealth.model;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class DailyNetAssetSnapshot {
    private Long id;
    private Long userId;
    private LocalDate snapshotDate;
    private BigDecimal totalAssetCny;
    private BigDecimal totalLiabilityCny;
    private BigDecimal netAssetCny;
    private BigDecimal liquidCny;
    private BigDecimal fixedCny;
    private BigDecimal receivableCny;
    private BigDecimal investmentCny;
    private BigDecimal liabilityCny;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
