package com.homewealth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class UpdateCashBalanceRequest {
    @NotBlank(message = "币种不能为空")
    private String currency;

    @NotNull(message = "金额不能为空")
    private BigDecimal amount;

    private String note;
}
