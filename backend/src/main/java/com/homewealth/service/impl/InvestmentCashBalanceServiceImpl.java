package com.homewealth.service.impl;

import com.homewealth.dto.request.UpdateCashBalanceRequest;
import com.homewealth.dto.response.CashBalanceVO;
import com.homewealth.exception.BusinessException;
import com.homewealth.exception.ErrorCode;
import com.homewealth.mapper.AssetAccountMapper;
import com.homewealth.mapper.InvestmentCashBalanceMapper;
import com.homewealth.model.AssetAccount;
import com.homewealth.model.InvestmentCashBalance;
import com.homewealth.service.ExchangeRateService;
import com.homewealth.service.InvestmentCashBalanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InvestmentCashBalanceServiceImpl implements InvestmentCashBalanceService {

    private final InvestmentCashBalanceMapper cashMapper;
    private final AssetAccountMapper accountMapper;
    private final ExchangeRateService exchangeRateService;

    @Override
    public List<CashBalanceVO> listByAccount(Long userId, Long accountId) {
        validateInvestmentAccount(accountId, userId);
        return cashMapper.findByAccountId(accountId).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public CashBalanceVO upsert(Long userId, Long accountId, UpdateCashBalanceRequest request) {
        validateInvestmentAccount(accountId, userId);

        String currency = request.getCurrency().toUpperCase();
        BigDecimal cnyRate = exchangeRateService.getRate(currency, "CNY");
        BigDecimal cnyAmount = request.getAmount().multiply(cnyRate);

        InvestmentCashBalance existing = cashMapper.findByAccountAndCurrency(accountId, currency);
        if (existing != null) {
            existing.setAmount(request.getAmount());
            existing.setCnyRate(cnyRate);
            existing.setCnyAmount(cnyAmount);
            existing.setNote(request.getNote());
            cashMapper.update(existing);
            return toVO(existing);
        } else {
            InvestmentCashBalance cash = new InvestmentCashBalance();
            cash.setAccountId(accountId);
            cash.setUserId(userId);
            cash.setCurrency(currency);
            cash.setAmount(request.getAmount());
            cash.setCnyRate(cnyRate);
            cash.setCnyAmount(cnyAmount);
            cash.setNote(request.getNote());
            cashMapper.insert(cash);
            return toVO(cash);
        }
    }

    @Override
    @Transactional
    public void delete(Long userId, Long accountId, Long id) {
        validateInvestmentAccount(accountId, userId);
        InvestmentCashBalance cash = cashMapper.findById(id);
        if (cash == null || !cash.getAccountId().equals(accountId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        cashMapper.deleteById(id, userId);
    }

    @Override
    public BigDecimal getTotalCnyByAccount(Long accountId) {
        List<InvestmentCashBalance> balances = cashMapper.findByAccountId(accountId);
        return balances.stream()
                .map(b -> {
                    BigDecimal rate = exchangeRateService.getRate(b.getCurrency(), "CNY");
                    return b.getAmount().multiply(rate);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public BigDecimal getTotalCnyByUser(Long userId) {
        List<InvestmentCashBalance> balances = cashMapper.findByUserId(userId);
        return balances.stream()
                .map(b -> {
                    BigDecimal rate = exchangeRateService.getRate(b.getCurrency(), "CNY");
                    return b.getAmount().multiply(rate);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void validateInvestmentAccount(Long accountId, Long userId) {
        AssetAccount account = accountMapper.findById(accountId);
        if (account == null || !account.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
        if (!"INVESTMENT".equals(account.getAccountType())) {
            throw new BusinessException(ErrorCode.NOT_INVESTMENT_ACCOUNT);
        }
    }

    private CashBalanceVO toVO(InvestmentCashBalance cash) {
        CashBalanceVO vo = new CashBalanceVO();
        vo.setId(cash.getId());
        vo.setAccountId(cash.getAccountId());
        vo.setCurrency(cash.getCurrency());
        vo.setAmount(cash.getAmount());
        vo.setCnyRate(cash.getCnyRate());
        vo.setCnyAmount(cash.getCnyAmount());
        vo.setNote(cash.getNote());
        vo.setUpdatedAt(cash.getUpdatedAt());
        return vo;
    }
}
