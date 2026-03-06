package com.homewealth.mapper;

import com.homewealth.model.DailyNetAssetSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface DailyNetAssetSnapshotMapper {
    DailyNetAssetSnapshot findByUserIdAndDate(@Param("userId") Long userId,
                                               @Param("date") LocalDate date);
    List<DailyNetAssetSnapshot> findByUserId(@Param("userId") Long userId,
                                              @Param("startDate") LocalDate startDate,
                                              @Param("endDate") LocalDate endDate);
    void upsert(DailyNetAssetSnapshot snapshot);
    void deleteByUserIdAndDate(@Param("userId") Long userId, @Param("date") LocalDate date);
}
