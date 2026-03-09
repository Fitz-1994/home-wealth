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

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
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

    /** JSON 格式 prompt（用于 Anthropic / OpenAI-compatible） */
    private static final String PROMPT_JSON =
            "请分析这张投资账户持仓截图，提取所有持仓数据。\n\n" +
            "以JSON数组格式返回，每个元素包含以下字段（无法识别的字段设为null）：\n" +
            "- symbol: 股票代码，转换为Yahoo Finance格式（A股上交所加.SS后缀如600519.SS，深交所加.SZ如000858.SZ，港股加.HK如0700.HK，美股直接用代码如AAPL）\n" +
            "- symbolName: 股票/基金名称\n" +
            "- quantity: 持仓数量（股数/份数，纯数字，去除逗号）\n" +
            "- costPrice: 持仓成本价或均价（每股/份，纯数字）\n" +
            "- priceCurrency: 价格币种（A股=CNY，港股=HKD，美股=USD）\n" +
            "- market: 市场类型（A股=CN_A，港股=HK，美股=US，基金=CN_A）\n\n" +
            "只返回JSON数组，不要任何其他文字说明或markdown代码块。";

    /** 紧凑 pipe 格式 prompt（用于 MiniMax，token 受限） */
    private static final String PROMPT_PIPE =
            "从持仓截图提取每只股票，一行一条，严格5列用|分隔：\n" +
            "列1=Yahoo Finance代码(上交所.SS深交所.SZ港股.HK美股原代码)，" +
            "列2=股票名，列3=持仓数量，列4=每股成本价纯数字，列5=币种(CNY/HKD/USD)\n" +
            "例：600519.SS|贵州茅台|100|1452.31|CNY\n" +
            "只输出数据行不要其他。";

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

        log.info("Calling AI vision: provider={}, model={}, imageSize={}KB, mimeType={}",
                provider, model, imageBytes.length / 1024, mimeType);

        String responseText;
        if ("anthropic".equalsIgnoreCase(provider)) {
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            responseText = callAnthropic(base64Image, mimeType);
        } else if (apiUrl.contains("minimax")) {
            // MiniMax：压缩图片减少 prompt tokens，用紧凑 pipe 格式规避 256 completion token 限制
            byte[] compressed = compressImageForMiniMax(imageBytes);
            String base64Image = Base64.getEncoder().encodeToString(compressed);
            log.info("MiniMax: compressed image {}KB -> {}KB", imageBytes.length / 1024, compressed.length / 1024);
            responseText = callMiniMax(base64Image);
        } else {
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            responseText = callOpenAICompatible(base64Image, mimeType);
        }

        log.info("AI vision response (first 300 chars): {}",
                responseText.length() > 300 ? responseText.substring(0, 300) : responseText);

        if (apiUrl.contains("minimax")) {
            return parsePipeResponse(responseText);
        }
        return parseJsonResponse(responseText);
    }

    /**
     * 压缩图片：缩放到宽度最大 600px + JPEG 70% 质量，大幅减少 prompt tokens
     */
    private byte[] compressImageForMiniMax(byte[] imageBytes) {
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (src == null) return imageBytes;

            int maxWidth = 600;
            BufferedImage img;
            if (src.getWidth() > maxWidth) {
                int newH = (int) ((double) src.getHeight() * maxWidth / src.getWidth());
                img = new BufferedImage(maxWidth, newH, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(src, 0, 0, maxWidth, newH, null);
                g.dispose();
            } else {
                img = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                g.drawImage(src, 0, 0, null);
                g.dispose();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
            if (!writers.hasNext()) return imageBytes;
            ImageWriter writer = writers.next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.7f);
            writer.setOutput(ImageIO.createImageOutputStream(out));
            writer.write(null, new IIOImage(img, null, null), param);
            writer.dispose();
            return out.toByteArray();
        } catch (Exception e) {
            log.warn("Image compression failed, using original: {}", e.getMessage());
            return imageBytes;
        }
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
                                Map.of("type", "text", "text", PROMPT_JSON)
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

    /** OpenAI 兼容格式（OpenAI / DeepSeek 等） */
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
                                Map.of("type", "text", "text", PROMPT_JSON)
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
     * MiniMax 专用（/v1/text/chatcompletion_v2 原生格式）：
     * 使用紧凑 pipe 格式规避 256 completion token 限制
     */
    private String callMiniMax(String base64Image) {
        log.info("MiniMax: calling {} with pipe-format prompt", apiUrl);

        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "tokens_to_generate", 4096,
                    "messages", List.of(Map.of(
                            "role", "user",
                            "content", List.of(
                                    Map.of("type", "image_url", "image_url", Map.of(
                                            "url", "data:image/jpeg;base64," + base64Image
                                    )),
                                    Map.of("type", "text", "text", PROMPT_PIPE)
                            )
                    ))
            );

            String json = objectMapper.writeValueAsString(requestBody);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            ResponseEntity<Map> response = aiRestTemplate().postForEntity(
                    apiUrl, new HttpEntity<>(json, headers), Map.class);

            Map<?, ?> body = response.getBody();
            if (body == null) throw new BusinessException(ErrorCode.INTERNAL_ERROR, "MiniMax 返回空响应");

            List<Map<?, ?>> choices = (List<Map<?, ?>>) body.get("choices");
            if (choices == null || choices.isEmpty())
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "MiniMax 返回无效响应: " + body);

            String finishReason = (String) choices.get(0).get("finish_reason");
            if ("length".equals(finishReason)) {
                log.warn("MiniMax response truncated (finish_reason=length), result may be incomplete");
            }
            Map<?, ?> message = (Map<?, ?>) choices.get(0).get("message");
            return (String) message.get("content");

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("MiniMax API failed", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "MiniMax API 调用失败: " + e.getMessage());
        }
    }

    /**
     * 解析 pipe 格式响应（MiniMax 专用）：
     * 每行 5 列：symbol|name|quantity|costPrice|currency
     * market 和 lotSize 从 symbol 后缀推断
     */
    private List<ParsedHoldingVO> parsePipeResponse(String text) {
        if (text == null || text.isBlank())
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI返回内容为空");

        List<ParsedHoldingVO> result = new ArrayList<>();
        for (String line : text.trim().split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split("\\|");
            if (parts.length < 4) {
                log.warn("Skipping malformed line: {}", line);
                continue;
            }
            try {
                ParsedHoldingVO vo = new ParsedHoldingVO();
                vo.setSymbol(parts[0].trim());
                vo.setSymbolName(parts[1].trim());
                vo.setQuantity(new BigDecimal(parts[2].trim().replaceAll(",", "")));
                vo.setCostPrice(new BigDecimal(parts[3].trim().replaceAll(",", "")));
                vo.setPriceCurrency(parts.length > 4 ? parts[4].trim() : inferCurrency(vo.getSymbol()));
                vo.setMarket(inferMarket(vo.getSymbol()));
                result.add(vo);
            } catch (Exception e) {
                log.warn("Skipping unparseable line '{}': {}", line, e.getMessage());
            }
        }
        if (result.isEmpty())
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "未能从图片中识别到持仓数据");
        return result;
    }

    private String inferMarket(String symbol) {
        if (symbol == null) return "CN_A";
        String s = symbol.toUpperCase();
        if (s.endsWith(".SS") || s.endsWith(".SZ")) return "CN_A";
        if (s.endsWith(".HK")) return "HK";
        if (s.endsWith("=X")) return "FX";
        return "US";
    }

    private String inferCurrency(String symbol) {
        String market = inferMarket(symbol);
        return switch (market) {
            case "HK", "HK_OPT" -> "HKD";
            case "US", "US_OPT" -> "USD";
            default -> "CNY";
        };
    }

    private List<ParsedHoldingVO> parseJsonResponse(String text) {
        if (text == null || text.isBlank())
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI返回内容为空");

        // 剥除 reasoning model 的 <think>...</think> 块（DeepSeek 等）
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
