package com.homewealth.service.impl;

import com.homewealth.dto.request.CreateApiKeyRequest;
import com.homewealth.dto.request.LoginRequest;
import com.homewealth.dto.request.RegisterRequest;
import com.homewealth.dto.response.LoginResponse;
import com.homewealth.exception.BusinessException;
import com.homewealth.exception.ErrorCode;
import com.homewealth.mapper.ApiKeyMapper;
import com.homewealth.mapper.UserMapper;
import com.homewealth.model.ApiKey;
import com.homewealth.model.User;
import com.homewealth.security.JwtAuthFilter;
import com.homewealth.security.JwtTokenProvider;
import com.homewealth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final ApiKeyMapper apiKeyMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${jwt.expiration:86400000}")
    private long jwtExpiration;

    @Override
    public LoginResponse login(LoginRequest request) {
        User user = userMapper.findByUsername(request.getUsername());
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (!user.getEnabled()) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_INCORRECT);
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername());

        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setExpiresIn(jwtExpiration / 1000);

        LoginResponse.UserVO userVO = new LoginResponse.UserVO();
        userVO.setId(user.getId());
        userVO.setUsername(user.getUsername());
        userVO.setDisplayName(user.getDisplayName());
        userVO.setRole(user.getRole());
        response.setUser(userVO);

        return response;
    }

    @Override
    public User register(RegisterRequest request) {
        if (userMapper.findByUsername(request.getUsername()) != null) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setDisplayName(request.getDisplayName() != null ? request.getDisplayName() : request.getUsername());
        user.setRole("USER");
        user.setEnabled(true);
        userMapper.insert(user);
        return user;
    }

    @Override
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userMapper.findById(userId);
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_INCORRECT);
        }
        userMapper.updatePassword(userId, passwordEncoder.encode(newPassword));
    }

    @Override
    public Map<String, String> createApiKey(Long userId, CreateApiKeyRequest request) {
        // 生成安全随机 key：格式 hw_sk_<32字节随机base64url>
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        String rawKey = "hw_sk_" + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        String hashedKey = JwtAuthFilter.sha256(rawKey);
        String prefix = rawKey.substring(0, Math.min(rawKey.length(), 12));

        ApiKey apiKey = new ApiKey();
        apiKey.setUserId(userId);
        apiKey.setKeyName(request.getKeyName());
        apiKey.setKeyValue(hashedKey);
        apiKey.setKeyPrefix(prefix);
        apiKey.setIsActive(true);
        apiKey.setExpiresAt(request.getExpiresAt());
        apiKeyMapper.insert(apiKey);

        // 完整明文 key 仅此时返回一次
        return Map.of("id", String.valueOf(apiKey.getId()), "key", rawKey, "prefix", prefix);
    }

    @Override
    public List<ApiKey> listApiKeys(Long userId) {
        return apiKeyMapper.findByUserId(userId);
    }

    @Override
    public void revokeApiKey(Long userId, Long keyId) {
        ApiKey key = apiKeyMapper.findById(keyId);
        if (key == null || !key.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.API_KEY_NOT_FOUND);
        }
        apiKeyMapper.deactivate(keyId, userId);
    }
}
