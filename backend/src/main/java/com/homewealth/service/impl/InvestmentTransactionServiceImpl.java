package com.homewealth.service.impl;

import com.homewealth.dto.request.CreateTransactionRequest;
import com.homewealth.dto.response.TransactionVO;
import com.homewealth.enums.InvestmentTxnType;
import com.homewealth.exception.BusinessException;
import com.homewealth.exception.ErrorCode;
import com.homewealth.mapper.AssetAccountMapper;
import com.homewealth.mapper.InvestmentCashBalanceMapper;
import com.homewealth.mapper.InvestmentHoldingMapper;
import com.homewealth.mapper.InvestmentTransactionMapper;
import com.homewealth.model.AssetAccount;
import com.homewealth.model.InvestmentCashBalance;
import com.homewealth.model.InvestmentHolding;
import com.homewealth.model.InvestmentTransaction;
import com.homewealth.service.ExchangeRateService;
import com.homewealth.service.InvestmentTransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvestmentTransactionServiceImpl implements InvestmentTransactionService {

    private static final int CASH_SCALE = 4;
    private static final int PRICE_SCALE = 6;

    private final InvestmentTransactionMapper txnMapper;
    private final InvestmentHoldingMapper holdingMapper;
    private final InvestmentCashBalanceMapper cashMapper;
    private final AssetAccountMapper accountMapper;
    private final ExchangeRateService exchangeRateService;

    @Override
    @Transactional
    public TransactionVO create(Long userId, CreateTransactionRequest request) {
        validateInvestmentAccount(request.getAccountId(), userId);

        InvestmentTxnType type;
        try {
            type = InvestmentTxnType.valueOf(request.getTxnType());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.TRANSACTION_TYPE_INVALID);
        }

        String currency = request.getCurrency().toUpperCase();
        BigDecimal cnyRate = request.getCnyRate() != null
                ? request.getCnyRate()
                : exchangeRateService.getRate(currency, "CNY");
        BigDecimal amount = request.getAmount();
        BigDecimal fee = request.getFee() != null ? request.getFee() : BigDecimal.ZERO;
        BigDecimal amountCny = amount.multiply(cnyRate).setScale(CASH_SCALE, RoundingMode.HALF_UP);

        InvestmentTransaction txn = new InvestmentTransaction();
        txn.setUserId(userId);
        txn.setAccountId(request.getAccountId());
        txn.setHoldingId(request.getHoldingId());
        txn.setTxnType(type.name());
        txn.setSymbol(request.getSymbol());
        txn.setMarket(request.getMarket());
        txn.setTradeDate(request.getTradeDate() != null ? request.getTradeDate() : LocalDate.now());
        txn.setQuantity(request.getQuantity());
        txn.setPrice(request.getPrice());
        txn.setAmount(amount);
        txn.setFee(fee);
        txn.setCurrency(currency);
        txn.setCnyRate(cnyRate);
        txn.setAmountCny(amountCny);
        txn.setIsSynthetic(Boolean.TRUE.equals(request.getSynthetic()));
        txn.setNote(request.getNote());

        switch (type) {
            case BUY:      applyBuy(txn);      break;
            case SELL:     applySell(txn);     break;
            case DIVIDEND: applyDividend(txn); break;
            case FEE:      applyFee(txn);      break;
            case CASH_IN:  applyCashIn(txn);   break;
            case CASH_OUT: applyCashOut(txn);  break;
            case OPENING:  /* 仅写流水 */     break;
        }

        txnMapper.insert(txn);
        return toVO(txn);
    }

    @Override
    @Transactional
    public TransactionVO update(Long userId, Long id, CreateTransactionRequest request) {
        InvestmentTransaction existing = txnMapper.findById(id);
        if (existing == null || !existing.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND);
        }
        // 简化：仅允许修改备注/日期等元信息，不允许改类型/金额（避免回滚副作用复杂化）。
        existing.setTradeDate(request.getTradeDate() != null ? request.getTradeDate() : existing.getTradeDate());
        existing.setSymbol(request.getSymbol() != null ? request.getSymbol() : existing.getSymbol());
        existing.setMarket(request.getMarket() != null ? request.getMarket() : existing.getMarket());
        existing.setNote(request.getNote());
        txnMapper.update(existing);
        return toVO(existing);
    }

    @Override
    @Transactional
    public void delete(Long userId, Long id) {
        InvestmentTransaction existing = txnMapper.findById(id);
        if (existing == null || !existing.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND);
        }
        // 删除流水不会自动回滚 holding/cash_balance 的副作用。
        // 用户应自行通过反向交易冲销。
        txnMapper.delete(id, userId);
    }

    @Override
    public Map<String, Object> page(Long userId, LocalDate from, LocalDate to,
                                     String txnType, Long accountId, Long holdingId,
                                     int page, int size) {
        int offset = Math.max(0, page) * size;
        List<InvestmentTransaction> rows = txnMapper.findByUser(userId, from, to, txnType, accountId, holdingId, size, offset);
        long total = txnMapper.countByUser(userId, from, to, txnType, accountId, holdingId);
        Map<Long, AssetAccount> accountCache = new HashMap<>();
        Map<Long, InvestmentHolding> holdingCache = new HashMap<>();
        List<TransactionVO> items = rows.stream().map(t -> toVO(t, accountCache, holdingCache)).collect(Collectors.toList());
        Map<String, Object> result = new HashMap<>();
        result.put("items", items);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    @Override
    @Transactional
    public int generateOpeningTransactions(Long userId) {
        List<InvestmentHolding> holdings = holdingMapper.findActiveByUserId(userId);
        int count = 0;
        for (InvestmentHolding h : holdings) {
            if (h.getCostPrice() == null || h.getQuantity() == null
                    || h.getQuantity().compareTo(BigDecimal.ZERO) == 0) continue;
            if (txnMapper.countOpeningByHolding(h.getId()) > 0) continue;

            String costCurr = resolveCostCurrency(h);
            BigDecimal amount = h.getCostPrice().multiply(h.getQuantity()).setScale(CASH_SCALE, RoundingMode.HALF_UP);
            BigDecimal cnyRate = exchangeRateService.getRate(costCurr, "CNY");

            InvestmentTransaction txn = new InvestmentTransaction();
            txn.setUserId(userId);
            txn.setAccountId(h.getAccountId());
            txn.setHoldingId(h.getId());
            txn.setTxnType(InvestmentTxnType.OPENING.name());
            txn.setSymbol(h.getSymbol());
            txn.setMarket(h.getMarket());
            txn.setTradeDate(LocalDate.now());
            txn.setQuantity(h.getQuantity());
            txn.setPrice(h.getCostPrice());
            txn.setAmount(amount);
            txn.setFee(BigDecimal.ZERO);
            txn.setCurrency(costCurr);
            txn.setCnyRate(cnyRate);
            txn.setAmountCny(amount.multiply(cnyRate).setScale(CASH_SCALE, RoundingMode.HALF_UP));
            txn.setIsSynthetic(true);
            txn.setNote("系统合成的开仓初始记录");
            txnMapper.insert(txn);
            count++;
        }
        log.info("Generated {} OPENING transactions for userId={}", count, userId);
        return count;
    }

    // ============ 各类型业务逻辑 ============

    private void applyBuy(InvestmentTransaction txn) {
        if (txn.getQuantity() == null || txn.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        InvestmentHolding holding = txn.getHoldingId() != null
                ? holdingMapper.findById(txn.getHoldingId())
                : (txn.getSymbol() != null
                    ? holdingMapper.findByAccountAndSymbol(txn.getAccountId(), txn.getSymbol())
                    : null);

        BigDecimal q = txn.getQuantity();
        BigDecimal totalCost = txn.getAmount(); // amount 已含 fee 由前端约定

        if (holding == null) {
            // 自动建仓
            holding = new InvestmentHolding();
            holding.setAccountId(txn.getAccountId());
            holding.setUserId(txn.getUserId());
            holding.setSymbol(txn.getSymbol());
            holding.setMarket(txn.getMarket());
            holding.setQuantity(q);
            holding.setCostPrice(totalCost.divide(q, PRICE_SCALE, RoundingMode.HALF_UP));
            holding.setCostCurrency(txn.getCurrency());
            holding.setPriceCurrency(txn.getCurrency());
            holdingMapper.insert(holding);
            txn.setHoldingId(holding.getId());
        } else {
            if (!holding.getUserId().equals(txn.getUserId())) {
                throw new BusinessException(ErrorCode.HOLDING_NOT_FOUND);
            }
            BigDecimal oldQ = holding.getQuantity() != null ? holding.getQuantity() : BigDecimal.ZERO;
            BigDecimal oldCostPrice = holding.getCostPrice() != null ? holding.getCostPrice() : BigDecimal.ZERO;
            BigDecimal newQ = oldQ.add(q);
            BigDecimal newBasis = oldCostPrice.multiply(oldQ).add(totalCost);
            BigDecimal newCostPrice = newQ.compareTo(BigDecimal.ZERO) > 0
                    ? newBasis.divide(newQ, PRICE_SCALE, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            holding.setQuantity(newQ);
            holding.setCostPrice(newCostPrice);
            if (holding.getCostCurrency() == null) holding.setCostCurrency(txn.getCurrency());
            holdingMapper.update(holding);
            txn.setHoldingId(holding.getId());
        }

        upsertCashBalance(txn.getAccountId(), txn.getUserId(), txn.getCurrency(), totalCost.negate());
    }

    private void applySell(InvestmentTransaction txn) {
        InvestmentHolding holding = txn.getHoldingId() != null
                ? holdingMapper.findById(txn.getHoldingId())
                : null;
        if (holding == null || !holding.getUserId().equals(txn.getUserId())) {
            throw new BusinessException(ErrorCode.HOLDING_NOT_FOUND);
        }
        if (txn.getQuantity() == null || txn.getQuantity().compareTo(BigDecimal.ZERO) <= 0
                || holding.getQuantity().compareTo(txn.getQuantity()) < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_QUANTITY);
        }

        BigDecimal newQ = holding.getQuantity().subtract(txn.getQuantity());
        holding.setQuantity(newQ);
        // cost_price 保持不变（成本平均法在卖出时不变）
        holdingMapper.update(holding);
        if (newQ.compareTo(BigDecimal.ZERO) == 0) {
            holdingMapper.deactivate(holding.getId(), txn.getUserId());
        }

        BigDecimal proceeds = txn.getAmount().subtract(txn.getFee() != null ? txn.getFee() : BigDecimal.ZERO);
        upsertCashBalance(txn.getAccountId(), txn.getUserId(), txn.getCurrency(), proceeds);
    }

    private void applyDividend(InvestmentTransaction txn) {
        InvestmentHolding holding = txn.getHoldingId() != null
                ? holdingMapper.findById(txn.getHoldingId())
                : null;
        if (holding == null || !holding.getUserId().equals(txn.getUserId())) {
            throw new BusinessException(ErrorCode.HOLDING_NOT_FOUND);
        }

        // 联动 cash_balance：增加分红
        upsertCashBalance(txn.getAccountId(), txn.getUserId(), txn.getCurrency(), txn.getAmount());

        // 联动 holding.cost_price：成本回收法
        if (holding.getCostPrice() != null && holding.getQuantity() != null
                && holding.getQuantity().compareTo(BigDecimal.ZERO) > 0
                && holding.getCostPrice().compareTo(BigDecimal.ZERO) > 0) {

            String costCurr = resolveCostCurrency(holding);
            BigDecimal divInCostCurr;
            if (costCurr.equals(txn.getCurrency())) {
                divInCostCurr = txn.getAmount();
            } else {
                BigDecimal costRate = exchangeRateService.getRate(costCurr, "CNY");
                if (costRate == null || costRate.compareTo(BigDecimal.ZERO) == 0) {
                    log.warn("Skip cost-recovery: cannot resolve rate for cost_currency={}", costCurr);
                    return;
                }
                divInCostCurr = txn.getAmountCny().divide(costRate, PRICE_SCALE, RoundingMode.HALF_UP);
            }

            BigDecimal oldBasis = holding.getCostPrice().multiply(holding.getQuantity());
            BigDecimal newBasis = oldBasis.subtract(divInCostCurr);
            if (newBasis.compareTo(BigDecimal.ZERO) < 0) newBasis = BigDecimal.ZERO;
            BigDecimal newCostPrice = newBasis.divide(holding.getQuantity(), PRICE_SCALE, RoundingMode.HALF_UP);
            holding.setCostPrice(newCostPrice);
            holdingMapper.update(holding);
        }
    }

    private void applyFee(InvestmentTransaction txn) {
        upsertCashBalance(txn.getAccountId(), txn.getUserId(), txn.getCurrency(), txn.getAmount().negate());
    }

    private void applyCashIn(InvestmentTransaction txn) {
        upsertCashBalance(txn.getAccountId(), txn.getUserId(), txn.getCurrency(), txn.getAmount());
    }

    private void applyCashOut(InvestmentTransaction txn) {
        upsertCashBalance(txn.getAccountId(), txn.getUserId(), txn.getCurrency(), txn.getAmount().negate());
    }

    // ============ Helpers ============

    private void upsertCashBalance(Long accountId, Long userId, String currency, BigDecimal delta) {
        currency = currency.toUpperCase();
        BigDecimal cnyRate = exchangeRateService.getRate(currency, "CNY");
        InvestmentCashBalance balance = cashMapper.findByAccountAndCurrency(accountId, currency);
        if (balance == null) {
            balance = new InvestmentCashBalance();
            balance.setAccountId(accountId);
            balance.setUserId(userId);
            balance.setCurrency(currency);
            balance.setAmount(delta);
            balance.setCnyRate(cnyRate);
            balance.setCnyAmount(delta.multiply(cnyRate).setScale(CASH_SCALE, RoundingMode.HALF_UP));
            cashMapper.insert(balance);
        } else {
            BigDecimal newAmount = balance.getAmount().add(delta);
            balance.setAmount(newAmount);
            balance.setCnyRate(cnyRate);
            balance.setCnyAmount(newAmount.multiply(cnyRate).setScale(CASH_SCALE, RoundingMode.HALF_UP));
            cashMapper.update(balance);
        }
    }

    private String resolveCostCurrency(InvestmentHolding h) {
        if (h.getCostCurrency() != null) return h.getCostCurrency();
        if (h.getPriceCurrency() != null) return h.getPriceCurrency();
        return "CNY";
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

    private TransactionVO toVO(InvestmentTransaction t) {
        return toVO(t, new HashMap<>(), new HashMap<>());
    }

    private TransactionVO toVO(InvestmentTransaction t, Map<Long, AssetAccount> acctCache, Map<Long, InvestmentHolding> holdingCache) {
        TransactionVO vo = new TransactionVO();
        vo.setId(t.getId());
        vo.setAccountId(t.getAccountId());
        vo.setHoldingId(t.getHoldingId());
        vo.setTxnType(t.getTxnType());
        vo.setSymbol(t.getSymbol());
        vo.setMarket(t.getMarket());
        vo.setTradeDate(t.getTradeDate());
        vo.setQuantity(t.getQuantity());
        vo.setPrice(t.getPrice());
        vo.setAmount(t.getAmount());
        vo.setFee(t.getFee());
        vo.setCurrency(t.getCurrency());
        vo.setCnyRate(t.getCnyRate());
        vo.setAmountCny(t.getAmountCny());
        vo.setIsSynthetic(t.getIsSynthetic());
        vo.setNote(t.getNote());
        vo.setCreatedAt(t.getCreatedAt());

        AssetAccount acct = acctCache.computeIfAbsent(t.getAccountId(), accountMapper::findById);
        if (acct != null) vo.setAccountName(acct.getAccountName());

        if (t.getHoldingId() != null) {
            InvestmentHolding h = holdingCache.computeIfAbsent(t.getHoldingId(), holdingMapper::findById);
            if (h != null) vo.setSymbolName(h.getSymbolName());
        }

        return vo;
    }
}
