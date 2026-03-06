package com.homewealth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class CreateHoldingRequest {
    @NotNull(message = "账户ID不能为空")
    private Long accountId;

    @NotBlank(message = "标的代码不能为空")
    private String symbol;

    private String symbolName;

    @NotBlank(message = "市场类型不能为空")
    private String market;          // CN_A / HK / US / HK_OPT / US_OPT / FX

    @NotNull(message = "持仓数量不能为空")
    @Positive(message = "持仓数量必须大于0")
    private BigDecimal quantity;

    private BigDecimal costPrice;
    private String costCurrency;

    @NotBlank(message = "价格币种不能为空")
    private String priceCurrency;

    private Integer lotSize = 1;
    private String note;
}
