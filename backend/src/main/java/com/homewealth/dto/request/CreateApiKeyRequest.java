package com.homewealth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CreateApiKeyRequest {
    @NotBlank(message = "Key名称不能为空")
    private String keyName;
    private LocalDateTime expiresAt;  // null 表示永不过期
}
