package com.homewealth.market;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class MarketQuote {
    private String symbol;
    private String symbolName;
    private BigDecimal price;
    private String currency;
    private BigDecimal changePct;
    private LocalDate tradeDate;
    private String source;
}
