package com.homewealth.market;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class DividendInfo {
    private String symbol;
    private LocalDate exDividendDate;
    private BigDecimal dividendPerShare;
    private String currency;
}
