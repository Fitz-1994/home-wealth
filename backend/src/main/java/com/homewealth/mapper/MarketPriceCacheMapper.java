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
}
