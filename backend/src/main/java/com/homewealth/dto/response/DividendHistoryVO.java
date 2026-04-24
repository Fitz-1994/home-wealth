package com.homewealth.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class DividendHistoryVO {
    private List<String> months;
    private List<BigDecimal> totals;
}
