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

    DividendIncomeRecord findOne(@Param("userId") Long userId,
                                  @Param("accountId") Long accountId,
                                  @Param("symbol") String symbol,
                                  @Param("exDividendDate") java.time.LocalDate exDividendDate);

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
