package com.homewealth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CreateTransactionRequest {

    @NotNull(message = "账户不能为空")
    private Long accountId;

    private Long holdingId;

    @NotBlank(message = "交易类型不能为空")
    private String txnType;

    private String symbol;
    private String market;

    private LocalDate tradeDate;

    private BigDecimal quantity;
    private BigDecimal price;

    @NotNull(message = "金额不能为空")
    private BigDecimal amount;

    private BigDecimal fee;

    @NotBlank(message = "币种不能为空")
    private String currency;

    private BigDecimal cnyRate;

    private String note;

    /**
     * 仅供内部调用：是否为系统合成（OPENING / DIVIDEND 联动）。
     * 前端无需传，默认 false。
     */
    private Boolean synthetic;
}
