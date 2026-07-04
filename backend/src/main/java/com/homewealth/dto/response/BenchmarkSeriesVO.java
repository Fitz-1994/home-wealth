package com.homewealth.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 基准指数日收盘序列（用于累计收益曲线对比）
 */
@Data
public class BenchmarkSeriesVO {

    private String symbol;

    private String name;

    private List<Point> points;

    @Data
    public static class Point {
        private LocalDate date;
        private BigDecimal close;
    }
}
