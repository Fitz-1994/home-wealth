package com.homewealth.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class TransactionVO {
    private Long id;
    private Long accountId;
    private String accountName;
    private Long holdingId;
    private String txnType;
    private String symbol;
    private String symbolName;
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
}
