package com.homewealth.mapper;

import com.homewealth.model.DailyInvestmentSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface DailyInvestmentSnapshotMapper {
    DailyInvestmentSnapshot findByUserIdAndDate(@Param("userId") Long userId,
                                                 @Param("date") LocalDate date);
    List<DailyInvestmentSnapshot> findByUserId(@Param("userId") Long userId,
                                                @Param("startDate") LocalDate startDate,
                                                @Param("endDate") LocalDate endDate);
    void upsert(DailyInvestmentSnapshot snapshot);
}
