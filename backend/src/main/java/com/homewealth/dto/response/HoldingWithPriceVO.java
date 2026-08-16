package com.homewealth.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
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
    private LocalDate priceTradeDate;  // 该价格对应的交易日，前端据此显示"价格过期 N 天"

    // Lombok 为 boolean isStale 生成 isStale()，Jackson 会去掉 is 前缀序列化成 "stale"，
    // 与前端和 skill 文档约定的 isStale 对不上 —— 前端的过期样式因此长期静默失效。
    @JsonProperty("isStale")
    private boolean isStale;
    private String priceSource;  // SINA / EASTMONEY / YAHOO / MANUAL，前端用于显示"手工价"标签
}
