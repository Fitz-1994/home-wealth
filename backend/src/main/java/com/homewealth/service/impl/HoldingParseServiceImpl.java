package com.homewealth.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homewealth.dto.response.ParsedHoldingVO;
import com.homewealth.exception.BusinessException;
import com.homewealth.exception.ErrorCode;
import com.homewealth.service.HoldingParseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class HoldingParseServiceImpl implements HoldingParseService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ai.vision.provider:anthropic}")
    private String provider;

    @Value("${ai.vision.api-key:}")
    private String apiKey;

    @Value("${ai.vision.api-url:https://api.anthropic.com/v1/messages}")
    private String apiUrl;

    @Value("${ai.vision.model:claude-sonnet-4-6}")
    private String model;

    private static final String PROMPT =
            "请分析这张投资账户持仓截图，提取所有持仓数据。\n\n" +
            "以JSON数组格式返回，每个元素包含以下字段（无法识别的字段设为null）：\n" +
            "- symbol: 股票代码，转换为Yahoo Finance格式（A股上交所加.SS后缀如600519.SS，深交所加.SZ如000858.SZ，港股加.HK如0700.HK，美股直接用代码如AAPL）\n" +
            "- symbolName: 股票/基金名称\n" +
            "- quantity: 持仓数量（股数/份数，纯数字，去除逗号）\n" +
            "- costPrice: 持仓成本价或均价（每股/份，纯数字）\n" +
            "- priceCurrency: 价格币种（A股=CNY，港股=HKD，美股=USD）\n" +
            "- market: 市场类型（A股=CN_A，港股=HK，美股=US，基金=CN_A）\n" +
            "- lotSize: 最小交易单位（A股=100，港股/美股=1）\n\n" +
            "只返回JSON数组，不要任何其他文字说明或markdown代码块。";

    /** 创建带 60s 超时的 RestTemplate（AI 推理耗时较长） */
    private RestTemplate aiRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(15000);
        factory.setReadTimeout(60000);
        return new RestTemplate(factory);
    }

    @Override
    public List<ParsedHoldingVO> parseFromImage(byte[] imageBytes, String mimeType) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI识别服务未配置（AI_API_KEY）");
        }

        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
        log.info("Calling AI vision: provider={}, model={}, imageSize={}KB, mimeType={}",
                provider, model, imageBytes.length / 1024, mimeType);

        String responseText;
        if ("anthropic".equalsIgnoreCase(provider)) {
            responseText = callAnthropic(base64Image, mimeType);
        } else if (apiUrl.contains("minimax")) {
            // MiniMax 不支持内联 base64，需要先上传图片获取 file_id
            responseText = callMiniMax(imageBytes, mimeType);
        } else {
            responseText = callOpenAICompatible(base64Image, mimeType);
        }

        log.info("AI vision response (first 300 chars): {}",
                responseText.length() > 300 ? responseText.substring(0, 300) : responseText);
        return parseJsonResponse(responseText);
    }

    /** Anthropic Messages API */
    private String callAnthropic(String base64Image, String mimeType) {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_tokens", 2048,
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of("type", "image", "source", Map.of(
                                        "type", "base64",
                                        "media_type", mimeType,
                                        "data", base64Image
                                )),
                                Map.of("type", "text", "text", PROMPT)
                        )
                ))
        );

        try {
            String json = objectMapper.writeValueAsString(requestBody);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-api-key", apiKey);
            headers.set("anthropic-version", "2023-06-01");

            ResponseEntity<Map> response = aiRestTemplate().postForEntity(
                    apiUrl, new HttpEntity<>(json, headers), Map.class);

            Map<?, ?> body = response.getBody();
            if (body == null) throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Anthropic API 返回空响应");

            List<Map<?, ?>> content = (List<Map<?, ?>>) body.get("content");
            if (content == null || content.isEmpty())
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Anthropic API 返回无效响应");

            return (String) content.get(0).get("text");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Anthropic API 调用失败: " + e.getMessage());
        }
    }

    /** OpenAI 兼容格式（MiniMax / OpenAI / DeepSeek 等） */
    private String callOpenAICompatible(String base64Image, String mimeType) {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_tokens", 2048,
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of("type", "image_url", "image_url", Map.of(
                                        "url", "data:" + mimeType + ";base64," + base64Image
                                )),
                                Map.of("type", "text", "text", PROMPT)
                        )
                ))
        );

        try {
            String json = objectMapper.writeValueAsString(requestBody);
            log.debug("OpenAI-compatible request body size: {} bytes", json.length());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            ResponseEntity<Map> response = aiRestTemplate().postForEntity(
                    apiUrl, new HttpEntity<>(json, headers), Map.class);

            Map<?, ?> body = response.getBody();
            if (body == null) throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI API 返回空响应");

            List<Map<?, ?>> choices = (List<Map<?, ?>>) body.get("choices");
            if (choices == null || choices.isEmpty())
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI API 返回无效响应");

            Map<?, ?> message = (Map<?, ?>) choices.get(0).get("message");
            return (String) message.get("content");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI API 调用失败: " + e.getMessage());
        }
    }

    /**
     * MiniMax 专用：先调 Files API 上传图片获取 file_id，再发起多模态对话
     * 文档：https://platform.minimaxi.com/document/Files
     */
    private String callMiniMax(byte[] imageBytes, String mimeType) {
        // 1. 上传图片
        String uploadUrl = apiUrl.replaceAll("/chat/completions.*", "") + "/files/upload";
        log.info("MiniMax: uploading image to {}", uploadUrl);

        try {
            HttpHeaders uploadHeaders = new HttpHeaders();
            uploadHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
            uploadHeaders.setBearerAuth(apiKey);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            // 包装成 ByteArrayResource 并指定文件名
            org.springframework.core.io.ByteArrayResource imageResource =
                    new org.springframework.core.io.ByteArrayResource(imageBytes) {
                        @Override public String getFilename() { return "holdings.png"; }
                    };
            body.add("file", imageResource);
            body.add("purpose", "retrieval");

            ResponseEntity<Map> uploadResp = aiRestTemplate().postForEntity(
                    uploadUrl, new HttpEntity<>(body, uploadHeaders), Map.class);

            Map<?, ?> uploadBody = uploadResp.getBody();
            if (uploadBody == null) throw new BusinessException(ErrorCode.INTERNAL_ERROR, "MiniMax 文件上传失败");

            Map<?, ?> fileInfo = (Map<?, ?>) uploadBody.get("file");
            if (fileInfo == null) throw new BusinessException(ErrorCode.INTERNAL_ERROR, "MiniMax 文件上传无响应");
            String fileId = (String) fileInfo.get("file_id");
            log.info("MiniMax: image uploaded, file_id={}", fileId);

            // 2. 发起对话，用 minimax-file:// 协议引用图片
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "max_tokens", 2048,
                    "messages", List.of(Map.of(
                            "role", "user",
                            "content", List.of(
                                    Map.of("type", "image_url", "image_url", Map.of(
                                            "url", "minimax-file://" + fileId
                                    )),
                                    Map.of("type", "text", "text", PROMPT)
                            )
                    ))
            );

            String json = objectMapper.writeValueAsString(requestBody);
            HttpHeaders chatHeaders = new HttpHeaders();
            chatHeaders.setContentType(MediaType.APPLICATION_JSON);
            chatHeaders.setBearerAuth(apiKey);

            ResponseEntity<Map> chatResp = aiRestTemplate().postForEntity(
                    apiUrl, new HttpEntity<>(json, chatHeaders), Map.class);

            Map<?, ?> chatBody = chatResp.getBody();
            if (chatBody == null) throw new BusinessException(ErrorCode.INTERNAL_ERROR, "MiniMax 对话返回空响应");

            List<Map<?, ?>> choices = (List<Map<?, ?>>) chatBody.get("choices");
            if (choices == null || choices.isEmpty())
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "MiniMax 对话返回无效响应");

            Map<?, ?> message = (Map<?, ?>) choices.get(0).get("message");
            return (String) message.get("content");

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("MiniMax API failed", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "MiniMax API 调用失败: " + e.getMessage());
        }
    }

    private List<ParsedHoldingVO> parseJsonResponse(String text) {
        if (text == null || text.isBlank())
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI返回内容为空");

        // 剥除 reasoning model 的 <think>...</think> 块（DeepSeek / MiniMax 等）
        String cleaned = text.replaceAll("(?s)<think>.*?</think>", "").trim();

        // 先尝试直接解析
        try {
            return objectMapper.readValue(cleaned, new TypeReference<List<ParsedHoldingVO>>() {});
        } catch (Exception ignored) {}

        // 从文本中提取 JSON 数组（防止模型多余的描述文字）
        int start = cleaned.indexOf('[');
        int end = cleaned.lastIndexOf(']');
        if (start >= 0 && end > start) {
            try {
                return objectMapper.readValue(cleaned.substring(start, end + 1),
                        new TypeReference<List<ParsedHoldingVO>>() {});
            } catch (Exception e) {
                log.error("JSON parse failed: {}", cleaned, e);
            }
        }

        // 截取模型回复前100字作为提示
        String preview = cleaned.length() > 100 ? cleaned.substring(0, 100) + "…" : cleaned;
        throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                "未能从图片中识别到持仓数据，模型回复：" + preview);
    }
}
