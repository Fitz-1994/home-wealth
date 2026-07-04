package com.homewealth.mapper;

import com.homewealth.model.InvestmentTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface InvestmentTransactionMapper {

    InvestmentTransaction findById(@Param("id") Long id);

    void insert(InvestmentTransaction txn);

    void update(InvestmentTransaction txn);

    void delete(@Param("id") Long id, @Param("userId") Long userId);

    List<InvestmentTransaction> findByUser(@Param("userId") Long userId,
                                            @Param("from") LocalDate from,
                                            @Param("to") LocalDate to,
                                            @Param("txnType") String txnType,
                                            @Param("accountId") Long accountId,
                                            @Param("holdingId") Long holdingId,
                                            @Param("limit") Integer limit,
                                            @Param("offset") Integer offset);

    long countByUser(@Param("userId") Long userId,
                     @Param("from") LocalDate from,
                     @Param("to") LocalDate to,
                     @Param("txnType") String txnType,
                     @Param("accountId") Long accountId,
                     @Param("holdingId") Long holdingId);

    /**
     * 当日净入金（仅 CASH_IN - CASH_OUT 进入此口径）。
     * 用于 daily_investment_snapshot.net_cashflow_cny。
     */
    BigDecimal sumNetCashflowByDate(@Param("userId") Long userId,
                                     @Param("date") LocalDate date);

    /**
     * 用于 OPENING 一次性迁移的幂等检查。
     */
    long countOpeningByHolding(@Param("holdingId") Long holdingId);
}
