package com.homewealth.mapper;

import com.homewealth.model.InvestmentHolding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface InvestmentHoldingMapper {
    InvestmentHolding findById(@Param("id") Long id);
    InvestmentHolding findByAccountAndSymbol(@Param("accountId") Long accountId, @Param("symbol") String symbol);
    List<InvestmentHolding> findByUserId(@Param("userId") Long userId,
                                          @Param("accountId") Long accountId,
                                          @Param("market") String market);
    List<InvestmentHolding> findActiveByUserId(@Param("userId") Long userId);
    // 获取系统中所有活跃持仓的 symbol 列表（用于定时更新行情）
    List<String> findAllActiveSymbols();
    void insert(InvestmentHolding holding);
    void update(InvestmentHolding holding);
    void updateSymbolName(@Param("symbol") String symbol, @Param("symbolName") String symbolName);
    void deactivate(@Param("id") Long id, @Param("userId") Long userId);
}
