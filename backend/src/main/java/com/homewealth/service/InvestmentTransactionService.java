package com.homewealth.service;

import com.homewealth.dto.request.CreateTransactionRequest;
import com.homewealth.dto.response.TransactionVO;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface InvestmentTransactionService {

    TransactionVO create(Long userId, CreateTransactionRequest request);

    TransactionVO update(Long userId, Long id, CreateTransactionRequest request);

    void delete(Long userId, Long id);

    Map<String, Object> page(Long userId, LocalDate from, LocalDate to,
                              String txnType, Long accountId, Long holdingId,
                              int page, int size);

    /**
     * 一次性为现有持仓生成 OPENING 合成交易（已生成的会跳过）。
     * @return 新生成的条数
     */
    int generateOpeningTransactions(Long userId);
}
