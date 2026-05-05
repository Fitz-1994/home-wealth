package com.homewealth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ManualPriceRequest {
    @NotBlank(message = "标的代码不能为空")
    private String symbol;

    @NotNull(message = "价格不能为空")
    @Positive(message = "价格必须大于 0")
    private BigDecimal price;

    private String currency;
}
