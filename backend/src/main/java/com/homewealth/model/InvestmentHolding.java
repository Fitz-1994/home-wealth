package com.homewealth.model;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class InvestmentHolding {
    private Long id;
    private Long accountId;
    private Long userId;
    private String symbol;
    private String symbolName;
    private String market;          // CN_A / HK / US / HK_OPT / US_OPT / FX
    private BigDecimal quantity;
    private BigDecimal costPrice;
    private String priceCurrency;
    private Integer lotSize;
    private Boolean isActive;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
