package com.homewealth.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class HoldingGroup {
    private Long id;
    private Long userId;
    private String groupName;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
