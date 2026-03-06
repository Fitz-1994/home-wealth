package com.homewealth.security;

import com.homewealth.mapper.ApiKeyMapper;
import com.homewealth.mapper.UserMapper;
import com.homewealth.model.ApiKey;
import com.homewealth.model.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsServiceImpl userDetailsService;
    private final ApiKeyMapper apiKeyMapper;
    private final UserMapper userMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 优先检查 X-API-Key 头
        String apiKeyHeader = request.getHeader("X-API-Key");
        if (StringUtils.hasText(apiKeyHeader)) {
            authenticateWithApiKey(apiKeyHeader, request);
        } else {
            // 其次检查 JWT Bearer Token
            String jwt = extractJwtFromRequest(request);
            if (StringUtils.hasText(jwt) && jwtTokenProvider.validateToken(jwt)) {
                authenticateWithJwt(jwt, request);
            }
        }

        filterChain.doFilter(request, response);
    }

    private void authenticateWithApiKey(String rawKey, HttpServletRequest request) {
        try {
            String hashedKey = sha256(rawKey);
            ApiKey apiKey = apiKeyMapper.findByKeyValue(hashedKey);
            if (apiKey == null || !apiKey.getIsActive()) {
                return;
            }
            if (apiKey.getExpiresAt() != null && apiKey.getExpiresAt().isBefore(LocalDateTime.now())) {
                return;
            }
            User user = userMapper.findById(apiKey.getUserId());
            if (user == null || !user.getEnabled()) {
                return;
            }
            // 更新最后使用时间（异步不阻塞请求，这里同步更新也可接受）
            apiKeyMapper.updateLastUsedAt(apiKey.getId(), LocalDateTime.now());

            setAuthentication(user, request);
        } catch (Exception e) {
            log.warn("API key authentication failed: {}", e.getMessage());
        }
    }

    private void authenticateWithJwt(String jwt, HttpServletRequest request) {
        try {
            String username = jwtTokenProvider.getUsernameFromToken(jwt);
            var userDetails = userDetailsService.loadUserByUsername(username);
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (Exception e) {
            log.warn("JWT authentication failed: {}", e.getMessage());
        }
    }

    private void setAuthentication(User user, HttpServletRequest request) {
        var authority = new SimpleGrantedAuthority("ROLE_" + user.getRole());
        var principal = new org.springframework.security.core.userdetails.User(
                user.getUsername(), "", List.of(authority));
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(authority));
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
