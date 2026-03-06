package com.homewealth.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AssetAccount {
    private Long id;
    private Long userId;
    private String accountName;
    private String accountType;     // REGULAR / INVESTMENT
    private String assetCategory;   // LIQUID / FIXED / RECEIVABLE / INVESTMENT / LIABILITY
    private String currency;
    private String description;
    private Integer sortOrder;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
