package com.homewealth.mapper;

import com.homewealth.model.InvestmentCashBalance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface InvestmentCashBalanceMapper {
    InvestmentCashBalance findById(@Param("id") Long id);
    InvestmentCashBalance findByAccountAndCurrency(@Param("accountId") Long accountId, @Param("currency") String currency);
    List<InvestmentCashBalance> findByAccountId(@Param("accountId") Long accountId);
    List<InvestmentCashBalance> findByUserId(@Param("userId") Long userId);
    void insert(InvestmentCashBalance cashBalance);
    void update(InvestmentCashBalance cashBalance);
    void deleteById(@Param("id") Long id, @Param("userId") Long userId);
    void deleteByAccountId(@Param("accountId") Long accountId);
}
