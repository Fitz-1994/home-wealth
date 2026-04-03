package com.homewealth.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class UpdateGroupMembersRequest {
    @NotNull(message = "成员列表不能为空")
    private List<String> symbols;
}
