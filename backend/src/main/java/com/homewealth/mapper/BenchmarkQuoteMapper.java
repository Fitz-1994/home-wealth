package com.homewealth.mapper;

import com.homewealth.model.BenchmarkQuote;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface BenchmarkQuoteMapper {

    void upsert(BenchmarkQuote quote);

    List<BenchmarkQuote> findBySymbol(@Param("symbol") String symbol);

    List<String> findDistinctSymbols();

    /** 清理早于指定日期的指数行情（对齐组合数据起始日） */
    void deleteBefore(@Param("date") LocalDate date);
}
