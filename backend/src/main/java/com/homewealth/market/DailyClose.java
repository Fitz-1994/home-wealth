package com.homewealth.market;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 单日收盘行情（历史日线数据点）
 */
@Data
@Builder
@AllArgsConstructor
public class DailyClose {

    private LocalDate date;

    private BigDecimal close;

    private String currency;
}
