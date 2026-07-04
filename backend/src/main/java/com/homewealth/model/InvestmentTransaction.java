package com.homewealth.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class InvestmentTransaction {
    private Long id;
    private Long userId;
    private Long accountId;
    private Long holdingId;
    private String txnType;
    private String symbol;
    private String market;
    private LocalDate tradeDate;
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal amount;
    private BigDecimal fee;
    private String currency;
    private BigDecimal cnyRate;
    private BigDecimal amountCny;
    private Boolean isSynthetic;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
