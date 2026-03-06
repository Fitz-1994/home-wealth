package com.homewealth.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ApiKey {
    private Long id;
    private Long userId;
    private String keyName;
    private String keyValue;    // SHA-256 哈希存储
    private String keyPrefix;   // 明文前缀，用于列表展示
    private Boolean isActive;
    private LocalDateTime lastUsedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}
