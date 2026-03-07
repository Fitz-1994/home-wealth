package com.homewealth.dto.response;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class HoldingWithPriceVO {
    private Long id;
    private Long accountId;
    private String symbol;
    private String symbolName;
    private String market;
    private BigDecimal quantity;
    private BigDecimal currentPrice;
    private String priceCurrency;
    private BigDecimal marketValueCny;
    private BigDecimal costPrice;
    private BigDecimal unrealizedPnl;
    private BigDecimal unrealizedPnlPct;
    private BigDecimal priceChangePct;
    private LocalDateTime priceUpdatedAt;
    private boolean isStale;
    private Integer lotSize;
}
