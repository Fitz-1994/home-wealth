package com.homewealth.service;

import com.homewealth.dto.request.CreateApiKeyRequest;
import com.homewealth.dto.request.LoginRequest;
import com.homewealth.dto.request.RegisterRequest;
import com.homewealth.dto.response.LoginResponse;
import com.homewealth.model.ApiKey;
import com.homewealth.model.User;

import java.util.List;
import java.util.Map;

public interface AuthService {
    LoginResponse login(LoginRequest request);
    User register(RegisterRequest request);
    void changePassword(Long userId, String oldPassword, String newPassword);

    // API Key 管理
    Map<String, String> createApiKey(Long userId, CreateApiKeyRequest request);  // 返回含完整明文key
    List<ApiKey> listApiKeys(Long userId);
    void revokeApiKey(Long userId, Long keyId);
}
