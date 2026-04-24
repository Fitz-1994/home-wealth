package com.homewealth.service;

import com.homewealth.dto.response.DividendDetailVO;
import com.homewealth.dto.response.DividendHistoryVO;
import com.homewealth.dto.response.DividendSummaryVO;

import java.time.LocalDate;
import java.util.List;

public interface DividendService {

    /** 从外部 API 抓取分红事件并存储 */
    void fetchAndStoreDividendEvents();

    /** 为指定用户在指定日期生成分红收入记录 */
    void generateDividendRecords(Long userId, LocalDate date);

    /** 回填指定月数内的分红收入记录（抓取 + 生成） */
    void backfillDividendRecords(Long userId, int months);

    /** 获取分红汇总（近12月、本年、本月） */
    DividendSummaryVO getDividendSummary(Long userId);

    /** 获取月度分红历史（图表数据） */
    DividendHistoryVO getDividendHistory(Long userId, int months);

    /** 获取指定时间段的分红明细（按标的聚合） */
    List<DividendDetailVO> getDividendDetail(Long userId, int year, Integer month);
}
