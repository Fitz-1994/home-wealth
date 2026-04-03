package com.homewealth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateHoldingGroupRequest {
    @NotBlank(message = "分组名称不能为空")
    private String groupName;

    private String note;
}
