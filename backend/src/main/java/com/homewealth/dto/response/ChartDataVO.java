package com.homewealth.dto.response;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class ChartDataVO {
    private List<String> dates;
    private List<BigDecimal> values;

    public static ChartDataVO from(List<LocalDate> dates, List<BigDecimal> values) {
        ChartDataVO vo = new ChartDataVO();
        vo.dates = dates.stream().map(LocalDate::toString).toList();
        vo.values = values;
        return vo;
    }
}
