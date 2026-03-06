package com.homewealth.service.impl;

import com.homewealth.dto.request.UpdateRecordRequest;
import com.homewealth.exception.BusinessException;
import com.homewealth.exception.ErrorCode;
import com.homewealth.mapper.AssetAccountMapper;
import com.homewealth.mapper.RegularAccountRecordMapper;
import com.homewealth.model.AssetAccount;
import com.homewealth.model.RegularAccountRecord;
import com.homewealth.service.ExchangeRateService;
import com.homewealth.service.RegularRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RegularRecordServiceImpl implements RegularRecordService {

    private final RegularAccountRecordMapper recordMapper;
    private final AssetAccountMapper accountMapper;
    private final ExchangeRateService exchangeRateService;

    @Override
    public RegularAccountRecord getCurrentRecord(Long userId, Long accountId) {
        validateAccount(userId, accountId);
        return recordMapper.findCurrentByAccountId(accountId);
    }

    @Override
    public List<RegularAccountRecord> getHistory(Long userId, Long accountId) {
        validateAccount(userId, accountId);
        return recordMapper.findByAccountId(accountId);
    }

    @Override
    @Transactional
    public RegularAccountRecord addRecord(Long userId, Long accountId, UpdateRecordRequest request) {
        AssetAccount account = validateAccount(userId, accountId);

        // 确定币种
        String currency = request.getCurrency() != null ? request.getCurrency() : account.getCurrency();
        BigDecimal cnyRate = exchangeRateService.getRate(currency, "CNY");
        BigDecimal cnyAmount = request.getAmount().multiply(cnyRate);

        // 将旧的 current 记录标记为非 current
        recordMapper.markAllNotCurrent(accountId);

        RegularAccountRecord record = new RegularAccountRecord();
        record.setAccountId(accountId);
        record.setUserId(userId);
        record.setAmount(request.getAmount());
        record.setCurrency(currency);
        record.setCnyRate(cnyRate);
        record.setCnyAmount(cnyAmount);
        record.setRecordDate(request.getRecordDate() != null ? request.getRecordDate() : LocalDate.now());
        record.setIsCurrent(true);
        record.setNote(request.getNote());
        record.setCreatedBy(userId);
        recordMapper.insert(record);
        return record;
    }

    @Override
    public void deleteRecord(Long userId, Long accountId, Long recordId) {
        validateAccount(userId, accountId);
        recordMapper.deleteById(recordId, accountId);
    }

    private AssetAccount validateAccount(Long userId, Long accountId) {
        AssetAccount account = accountMapper.findById(accountId);
        if (account == null || !account.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
        if ("INVESTMENT".equals(account.getAccountType())) {
            throw new BusinessException(ErrorCode.ACCOUNT_TYPE_INVALID, "投资账户不使用此接口");
        }
        return account;
    }
}
