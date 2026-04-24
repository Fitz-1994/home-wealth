package com.homewealth.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class DividendEvent {
    private Long id;
    private String symbol;
    private String market;
    private LocalDate exDividendDate;
    private LocalDate paymentDate;
    private BigDecimal dividendPerShare;
    private String currency;
    private String source;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
