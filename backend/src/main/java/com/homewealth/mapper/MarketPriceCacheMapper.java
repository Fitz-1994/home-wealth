package com.homewealth.mapper;

import com.homewealth.model.MarketPriceCache;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface MarketPriceCacheMapper {
    MarketPriceCache findLatestBySymbol(@Param("symbol") String symbol);
    List<MarketPriceCache> findLatestBySymbols(@Param("symbols") List<String> symbols);
    void upsert(MarketPriceCache cache);  // INSERT ON DUPLICATE KEY UPDATE
    void markStale(@Param("symbol") String symbol);
    void deleteOlderThan(@Param("date") LocalDate date);

    // 返回入参 symbols 中"最新一行 source=MANUAL"的子集，用于刷新时跳过
    List<String> findManualSymbols(@Param("symbols") List<String> symbols);

    // 删除某 symbol 全部 MANUAL 记录（恢复自动数据源）
    int deleteManualBySymbol(@Param("symbol") String symbol);
}
