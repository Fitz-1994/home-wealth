package com.homewealth.service;

import com.homewealth.dto.request.CreateHoldingRequest;
import com.homewealth.dto.response.HoldingWithPriceVO;

import java.util.List;

public interface InvestmentHoldingService {
    List<HoldingWithPriceVO> listHoldings(Long userId, Long accountId, String market);
    HoldingWithPriceVO getHolding(Long userId, Long holdingId);
    HoldingWithPriceVO createHolding(Long userId, CreateHoldingRequest request);
    HoldingWithPriceVO updateHolding(Long userId, Long holdingId, CreateHoldingRequest request);
    void closeHolding(Long userId, Long holdingId);
    HoldingWithPriceVO validateSymbol(String symbol, String priceCurrency);
}
