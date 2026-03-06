package com.homewealth.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class UpdateRecordRequest {
    @NotNull(message = "金额不能为空")
    private BigDecimal amount;

    private String currency;
    private LocalDate recordDate;
    private String note;
}
