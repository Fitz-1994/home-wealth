package com.homewealth.controller;

import com.homewealth.dto.request.CreateApiKeyRequest;
import com.homewealth.dto.request.LoginRequest;
import com.homewealth.dto.request.RegisterRequest;
import com.homewealth.dto.response.ApiResponse;
import com.homewealth.dto.response.LoginResponse;
import com.homewealth.model.ApiKey;
import com.homewealth.model.User;
import com.homewealth.security.SecurityUtils;
import com.homewealth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SecurityUtils securityUtils;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ApiResponse.success();
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me() {
        User user = securityUtils.getCurrentUser();
        return ApiResponse.success(Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "displayName", user.getDisplayName(),
                "role", user.getRole()
        ));
    }

    @PutMapping("/me/password")
    public ApiResponse<Void> changePassword(@RequestBody Map<String, String> body) {
        Long userId = securityUtils.getCurrentUserId();
        authService.changePassword(userId, body.get("oldPassword"), body.get("newPassword"));
        return ApiResponse.success();
    }

    // ---- API Key 管理 ----

    @GetMapping("/api-keys")
    public ApiResponse<List<ApiKey>> listApiKeys() {
        Long userId = securityUtils.getCurrentUserId();
        List<ApiKey> keys = authService.listApiKeys(userId);
        // 清除 keyValue（哈希值不对外暴露）
        keys.forEach(k -> k.setKeyValue(null));
        return ApiResponse.success(keys);
    }

    @PostMapping("/api-keys")
    public ApiResponse<Map<String, String>> createApiKey(@Valid @RequestBody CreateApiKeyRequest request) {
        Long userId = securityUtils.getCurrentUserId();
        Map<String, String> result = authService.createApiKey(userId, request);
        return ApiResponse.success(result);
    }

    @DeleteMapping("/api-keys/{id}")
    public ApiResponse<Void> revokeApiKey(@PathVariable Long id) {
        Long userId = securityUtils.getCurrentUserId();
        authService.revokeApiKey(userId, id);
        return ApiResponse.success();
    }
}
