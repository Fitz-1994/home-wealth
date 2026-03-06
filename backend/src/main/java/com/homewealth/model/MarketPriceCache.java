package com.homewealth.model;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class MarketPriceCache {
    private Long id;
    private String symbol;
    private String symbolName;
    private String market;
    private BigDecimal price;
    private String currency;
    private BigDecimal cnyRate;
    private BigDecimal cnyPrice;
    private BigDecimal changePct;
    private LocalDate tradeDate;
    private String source;
    private Boolean isStale;
    private LocalDateTime fetchedAt;
    private LocalDateTime updatedAt;
}
