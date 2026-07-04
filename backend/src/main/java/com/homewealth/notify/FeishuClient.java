package com.homewealth.notify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * 飞书自建应用消息推送（从 auto-trade 迁移）。
 *
 * 复刻 lark-oapi SDK 的两步原生 REST 调用（无 webhook、无 HMAC 签名）：
 * 1. 用 app_id + app_secret 换 tenant_access_token（有效期 7200s，内存缓存，提前 5 分钟过期重取）
 * 2. Bearer token 调 im/v1/messages?receive_id_type=chat_id 发消息（text / interactive 卡片）
 *
 * content 字段必须是卡片/文本对象的 JSON 字符串化。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeishuClient {

    private static final String TOKEN_URL =
            "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal";
    private static final String MESSAGE_URL =
            "https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=chat_id";

    private final ObjectMapper objectMapper;

    @Value("${feishu.enabled:false}")
    private boolean enabled;
    @Value("${feishu.app-id:}")
    private String appId;
    @Value("${feishu.app-secret:}")
    private String appSecret;
    @Value("${feishu.target-chat-id:}")
    private String targetChatId;

    private HttpClient httpClient;

    /** tenant_access_token 内存缓存 */
    private volatile String cachedToken;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    @PostConstruct
    void init() {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** 推送开关：总开关打开且密钥、目标群齐备 */
    public boolean isEnabled() {
        return enabled
                && appId != null && !appId.isBlank()
                && appSecret != null && !appSecret.isBlank()
                && targetChatId != null && !targetChatId.isBlank();
    }

    public String getTargetChatId() {
        return targetChatId;
    }

    /** 发送交互式卡片；chatId 为空则用配置的默认目标群 */
    public boolean sendCard(String chatId, Map<String, Object> card) {
        return send(chatId, "interactive", card);
    }

    /** 发送纯文本（备用/调试） */
    public boolean sendText(String chatId, String text) {
        return send(chatId, "text", Map.of("text", text));
    }

    // ---- 内部实现 ----

    private boolean send(String chatId, String msgType, Object content) {
        if (!isEnabled()) {
            log.debug("[Feishu] disabled, skip sending");
            return false;
        }
        String receiveId = (chatId != null && !chatId.isBlank()) ? chatId : targetChatId;
        try {
            String token = getTenantAccessToken();
            if (token == null) return false;

            // content 必须是对象的 JSON 字符串
            String contentJson = objectMapper.writeValueAsString(content);
            String body = objectMapper.writeValueAsString(Map.of(
                    "receive_id", receiveId,
                    "msg_type", msgType,
                    "content", contentJson));

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(MESSAGE_URL))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(resp.body());
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                log.warn("[Feishu] send message failed: code={}, msg={}",
                        code, root.path("msg").asText());
                return false;
            }
            log.info("[Feishu] {} message sent to {}", msgType, receiveId);
            return true;
        } catch (Exception e) {
            log.error("[Feishu] send message error: {}", e.getMessage());
            return false;
        }
    }

    /** 取 tenant_access_token，命中缓存直接返回，否则请求并缓存 */
    private synchronized String getTenantAccessToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt)) {
            return cachedToken;
        }
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "app_id", appId,
                    "app_secret", appSecret));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(TOKEN_URL))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(resp.body());
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                log.warn("[Feishu] fetch tenant_access_token failed: code={}, msg={}",
                        code, root.path("msg").asText());
                return null;
            }
            cachedToken = root.path("tenant_access_token").asText();
            long expire = root.path("expire").asLong(7200);
            // 提前 5 分钟过期，避免临界期用到失效 token
            tokenExpiresAt = Instant.now().plusSeconds(Math.max(60, expire - 300));
            return cachedToken;
        } catch (Exception e) {
            log.error("[Feishu] fetch tenant_access_token error: {}", e.getMessage());
            return null;
        }
    }
}
