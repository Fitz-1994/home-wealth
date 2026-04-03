package com.homewealth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class CreateHoldingGroupRequest {
    @NotBlank(message = "分组名称不能为空")
    private String groupName;

    private String note;

    /** 初始成员标的代码列表 */
    private List<String> symbols;
}
