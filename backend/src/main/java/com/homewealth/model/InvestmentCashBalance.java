package com.homewealth.model;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class InvestmentCashBalance {
    private Long id;
    private Long accountId;
    private Long userId;
    private String currency;
    private BigDecimal amount;
    private BigDecimal cnyRate;
    private BigDecimal cnyAmount;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
