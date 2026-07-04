package com.homewealth.service;

import com.homewealth.dto.response.DailyReturnVO;
import com.homewealth.dto.response.MonthlyReturnVO;
import com.homewealth.dto.response.ReturnSummaryVO;
import com.homewealth.dto.response.YearlyReturnVO;

import java.time.LocalDate;
import java.util.List;

public interface InvestmentReturnService {

    List<DailyReturnVO> getDailyReturns(Long userId, LocalDate from, LocalDate to);

    List<MonthlyReturnVO> getMonthlyReturns(Long userId, Integer year);

    List<YearlyReturnVO> getYearlyReturns(Long userId);

    ReturnSummaryVO getReturnSummary(Long userId);
}
