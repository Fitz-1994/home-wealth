package com.homewealth.dto.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class DividendDetailVO {
    private String symbol;
    private String symbolName;
    private String market;
    private BigDecimal totalDividendCny;
    private BigDecimal totalDividendOriginal;
    private String currency;
    private int eventCount;
}
