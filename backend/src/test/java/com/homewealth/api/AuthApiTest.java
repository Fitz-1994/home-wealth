package com.homewealth.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Auth API.
 * Requires a running MySQL (use -Dspring.profiles.active=dev).
 * Run: mvn test -pl backend -Dtest=AuthApiTest
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    // Shared across tests in this class
    static String token;

    private static final String TEST_USER = "test_api_" + System.currentTimeMillis();
    private static final String TEST_PASS = "Test@12345";

    @Test
    @Order(1)
    @DisplayName("POST /api/auth/register - 注册新用户成功")
    void register() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", TEST_USER,
                                "password", TEST_PASS,
                                "displayName", "测试用户"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(2)
    @DisplayName("POST /api/auth/register - 重复注册应失败")
    void register_duplicate() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", TEST_USER,
                                "password", TEST_PASS,
                                "displayName", "测试用户"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not(200)));
    }

    @Test
    @Order(3)
    @DisplayName("POST /api/auth/login - 登录成功并返回JWT")
    void login() throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", TEST_USER,
                                "password", TEST_PASS
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        token = objectMapper.readTree(body).path("data").path("token").asText();
        assertThat(token).isNotBlank().startsWith("ey");
    }

    @Test
    @Order(4)
    @DisplayName("POST /api/auth/login - 密码错误应失败")
    void login_wrongPassword() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", TEST_USER,
                                "password", "wrong_password"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not(200)));
    }

    @Test
    @Order(5)
    @DisplayName("GET /api/auth/me - 需要认证，未授权返回401")
    void me_unauthorized() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(6)
    @DisplayName("GET /api/auth/me - Bearer Token认证成功")
    void me_withToken() throws Exception {
        // login first if token not set (test isolation)
        if (token == null) loginForToken();

        mvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(TEST_USER));
    }

    @Test
    @Order(7)
    @DisplayName("POST /api/auth/api-keys - 创建API Key")
    void createApiKey() throws Exception {
        if (token == null) loginForToken();

        MvcResult result = mvc.perform(post("/api/auth/api-keys")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "keyName", "test-key"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.key").isNotEmpty())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String key = objectMapper.readTree(body).path("data").path("key").asText();
        assertThat(key).startsWith("hw_sk_");
    }

    @Test
    @Order(8)
    @DisplayName("GET /api/auth/api-keys - 列出API Key（不含明文）")
    void listApiKeys() throws Exception {
        if (token == null) loginForToken();

        mvc.perform(get("/api/auth/api-keys")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    private void loginForToken() throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", TEST_USER,
                                "password", TEST_PASS
                        ))))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        token = objectMapper.readTree(body).path("data").path("token").asText();
    }
}
