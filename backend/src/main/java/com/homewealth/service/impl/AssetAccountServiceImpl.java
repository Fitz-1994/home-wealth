package com.homewealth.service.impl;

import com.homewealth.dto.request.CreateAccountRequest;
import com.homewealth.enums.AccountType;
import com.homewealth.enums.AssetCategory;
import com.homewealth.exception.BusinessException;
import com.homewealth.exception.ErrorCode;
import com.homewealth.mapper.AssetAccountMapper;
import com.homewealth.model.AssetAccount;
import com.homewealth.service.AssetAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AssetAccountServiceImpl implements AssetAccountService {

    private final AssetAccountMapper accountMapper;

    @Override
    public List<AssetAccount> listAccounts(Long userId, String accountType, String assetCategory) {
        return accountMapper.findByUserId(userId, accountType, assetCategory);
    }

    @Override
    public AssetAccount getAccount(Long userId, Long accountId) {
        AssetAccount account = accountMapper.findById(accountId);
        if (account == null || !account.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
        return account;
    }

    @Override
    public AssetAccount createAccount(Long userId, CreateAccountRequest request) {
        validateAccountType(request.getAccountType(), request.getAssetCategory());

        AssetAccount account = new AssetAccount();
        account.setUserId(userId);
        account.setAccountName(request.getAccountName());
        account.setAccountType(request.getAccountType());
        account.setAssetCategory(request.getAssetCategory());
        account.setCurrency(request.getCurrency() != null ? request.getCurrency() : "CNY");
        account.setDescription(request.getDescription());
        account.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        account.setIsActive(true);
        accountMapper.insert(account);
        return account;
    }

    @Override
    public AssetAccount updateAccount(Long userId, Long accountId, CreateAccountRequest request) {
        AssetAccount account = getAccount(userId, accountId);
        // 不允许修改账户类型
        if (request.getAssetCategory() != null) {
            validateAccountType(account.getAccountType(), request.getAssetCategory());
            account.setAssetCategory(request.getAssetCategory());
        }
        if (request.getAccountName() != null) account.setAccountName(request.getAccountName());
        if (request.getCurrency() != null) account.setCurrency(request.getCurrency());
        if (request.getDescription() != null) account.setDescription(request.getDescription());
        if (request.getSortOrder() != null) account.setSortOrder(request.getSortOrder());
        accountMapper.update(account);
        return account;
    }

    @Override
    public void deleteAccount(Long userId, Long accountId) {
        getAccount(userId, accountId);
        accountMapper.deactivate(accountId, userId);
    }

    private void validateAccountType(String accountType, String assetCategory) {
        try {
            AccountType.valueOf(accountType);
            AssetCategory.valueOf(assetCategory);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.ACCOUNT_TYPE_INVALID);
        }
        if (AccountType.INVESTMENT.name().equals(accountType) &&
                !AssetCategory.INVESTMENT.name().equals(assetCategory)) {
            throw new BusinessException(ErrorCode.INVESTMENT_ACCOUNT_CATEGORY_MISMATCH);
        }
    }
}
