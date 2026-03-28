package com.homewealth.service;

import com.homewealth.dto.request.UpdateCashBalanceRequest;
import com.homewealth.dto.response.CashBalanceVO;

import java.math.BigDecimal;
import java.util.List;

public interface InvestmentCashBalanceService {
    List<CashBalanceVO> listByAccount(Long userId, Long accountId);
    CashBalanceVO upsert(Long userId, Long accountId, UpdateCashBalanceRequest request);
    void delete(Long userId, Long accountId, Long id);
    BigDecimal getTotalCnyByAccount(Long accountId);
    BigDecimal getTotalCnyByUser(Long userId);
}
