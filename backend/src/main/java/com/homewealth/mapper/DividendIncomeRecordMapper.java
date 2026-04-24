package com.homewealth.mapper;

import com.homewealth.model.DividendIncomeRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface DividendIncomeRecordMapper {

    void upsert(DividendIncomeRecord record);

    void batchUpsert(@Param("records") List<DividendIncomeRecord> records);

    BigDecimal findTotal(@Param("userId") Long userId,
                         @Param("startDate") LocalDate startDate,
                         @Param("endDate") LocalDate endDate);

    List<Map<String, Object>> findMonthlyAggregation(@Param("userId") Long userId,
                                                      @Param("startDate") LocalDate startDate,
                                                      @Param("endDate") LocalDate endDate);

    List<Map<String, Object>> findYearlyAggregation(@Param("userId") Long userId,
                                                     @Param("startDate") LocalDate startDate,
                                                     @Param("endDate") LocalDate endDate);

    List<Map<String, Object>> findDetailByPeriod(@Param("userId") Long userId,
                                                  @Param("startDate") LocalDate startDate,
                                                  @Param("endDate") LocalDate endDate);
}
