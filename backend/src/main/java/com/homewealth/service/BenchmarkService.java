package com.homewealth.service;

import com.homewealth.dto.response.BenchmarkInfoVO;
import com.homewealth.dto.response.BenchmarkReturnVO;
import com.homewealth.dto.response.BenchmarkSeriesVO;

import java.time.LocalDate;
import java.util.List;

public interface BenchmarkService {

    /** 抓取所有配置的基准指数近几日收盘并入库（每日调度，自动补齐节假日缺口） */
    void fetchAndSaveAll();

    /** 回填所有基准指数的历史日线（首次部署或补数据用，range 约 2 年） */
    void backfillHistory();

    /** 已配置的基准指数列表（含最新点位） */
    List<BenchmarkInfoVO> listBenchmarks();

    /**
     * 基准指数的周期收益率。
     * @param granularity "monthly" | "yearly"
     * @param year monthly 时按年过滤，可空
     */
    List<BenchmarkReturnVO> getReturns(String symbol, String granularity, Integer year);

    /** 所有基准指数的日收盘序列（用于累计收益曲线对比），from/to 可空 */
    List<BenchmarkSeriesVO> getDailySeries(LocalDate from, LocalDate to);
}
