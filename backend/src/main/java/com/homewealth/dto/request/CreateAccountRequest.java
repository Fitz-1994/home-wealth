package com.homewealth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateAccountRequest {
    @NotBlank(message = "账户名称不能为空")
    private String accountName;

    @NotBlank(message = "账户类型不能为空")
    private String accountType;      // REGULAR / INVESTMENT

    @NotBlank(message = "资产类型不能为空")
    private String assetCategory;    // LIQUID / FIXED / RECEIVABLE / INVESTMENT / LIABILITY

    private String currency = "CNY";
    private String description;
    private Integer sortOrder = 0;
}
