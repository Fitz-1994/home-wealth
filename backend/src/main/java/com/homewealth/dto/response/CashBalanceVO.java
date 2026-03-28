package com.homewealth.dto.response;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CashBalanceVO {
    private Long id;
    private Long accountId;
    private String currency;
    private BigDecimal amount;
    private BigDecimal cnyRate;
    private BigDecimal cnyAmount;
    private String note;
    private LocalDateTime updatedAt;
}
