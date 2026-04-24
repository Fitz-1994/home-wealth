package com.homewealth.dto.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class DividendSummaryVO {
    private BigDecimal last12MonthsTotal;
    private BigDecimal currentYearTotal;
    private BigDecimal currentMonthTotal;
}
