package com.homewealth.model;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class RegularAccountRecord {
    private Long id;
    private Long accountId;
    private Long userId;
    private BigDecimal amount;
    private String currency;
    private BigDecimal cnyRate;
    private BigDecimal cnyAmount;
    private LocalDate recordDate;
    private Boolean isCurrent;
    private String note;
    private LocalDateTime createdAt;
    private Long createdBy;
}
