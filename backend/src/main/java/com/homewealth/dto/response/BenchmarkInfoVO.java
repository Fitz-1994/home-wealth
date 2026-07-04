package com.homewealth.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class BenchmarkInfoVO {
    private String symbol;
    private String name;
    private BigDecimal latestClose;
    private LocalDate latestDate;
}
