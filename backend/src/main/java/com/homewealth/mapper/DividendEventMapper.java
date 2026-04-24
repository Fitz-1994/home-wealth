package com.homewealth.mapper;

import com.homewealth.model.DividendEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface DividendEventMapper {

    void upsert(DividendEvent event);

    void batchUpsert(@Param("events") List<DividendEvent> events);

    List<DividendEvent> findBySymbolsAndDate(@Param("symbols") List<String> symbols,
                                              @Param("date") LocalDate date);

    List<DividendEvent> findBySymbolsAndDateRange(@Param("symbols") List<String> symbols,
                                                   @Param("startDate") LocalDate startDate,
                                                   @Param("endDate") LocalDate endDate);
}
