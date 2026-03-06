package com.homewealth.mapper;

import com.homewealth.model.ExchangeRate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ExchangeRateMapper {
    ExchangeRate findLatest(@Param("fromCurrency") String fromCurrency,
                             @Param("toCurrency") String toCurrency);
    List<ExchangeRate> findAllLatest();
    void upsert(ExchangeRate rate);
}
