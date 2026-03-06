package com.homewealth.service;

import com.homewealth.dto.request.CreateAccountRequest;
import com.homewealth.model.AssetAccount;

import java.util.List;

public interface AssetAccountService {
    List<AssetAccount> listAccounts(Long userId, String accountType, String assetCategory);
    AssetAccount getAccount(Long userId, Long accountId);
    AssetAccount createAccount(Long userId, CreateAccountRequest request);
    AssetAccount updateAccount(Long userId, Long accountId, CreateAccountRequest request);
    void deleteAccount(Long userId, Long accountId);
}
