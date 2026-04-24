package com.homewealth.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class DividendIncomeRecord {
    private Long id;
    private Long userId;
    private Long accountId;
    private String symbol;
    private String symbolName;
    private String market;
    private LocalDate exDividendDate;
    private BigDecimal quantity;
    private BigDecimal dividendPerShare;
    private BigDecimal dividendAmount;
    private String currency;
    private BigDecimal cnyRate;
    private BigDecimal dividendAmountCny;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
