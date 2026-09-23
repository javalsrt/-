package com.coursenote.ws;

import com.coursenote.config.JwtUtil;
import com.coursenote.entity.AiChatRecord;
import com.coursenote.mapper.AiChatRecordMapper;
import com.coursenote.service.AIService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import reactor.core.publisher.Flux;

/**
 * AI知识库对话WebSocket处理器
 * 使用OpenAI兼容流式API（支持DeepSeek/通义千问/GLM-4等）
 */
@Slf4j
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private AIService aiService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private AiChatRecordMapper chatRecordMapper;

    @Value("${ai.llm.api-key}")
    private String llmApiKey;

    @Value("${ai.llm.api-url}")
    private String llmApiUrl;

    @Value("${ai.llm.model}")
    private String llmModel;

    private final WebClient webClient = WebClient.builder().build();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = resolveUserIdFromSession(session);
        if (userId != null) {
            session.getAttributes().put("userId", userId);
        }
        sessions.put(session.getId(), session);
        log.info("Chat WS connected: {}, userId={}", session.getId(), userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) throws IOException {
        String payload = textMessage.getPayload();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> msg = objectMapper.readValue(payload, Map.class);

            Long courseId = msg.get("courseId") != null ? ((Number) msg.get("courseId")).longValue() : null;
            // 安全：userId 只取自握手时 token 解析结果，忽略消息体中客户端自报的 userId，防止身份伪造
            Long userId = (Long) session.getAttributes().get("userId");
            if (userId == null) {
                session.sendMessage(new TextMessage("[ERROR]未登录或token无效"));
                return;
            }
            String userMessage = (String) msg.get("message");
            @SuppressWarnings("unchecked")
            List<Map<String, String>> history = (List<Map<String, String>>) msg.get("history");
            boolean useKnowledgeBase = msg.containsKey("useKnowledgeBase")
                    && Boolean.TRUE.equals(msg.get("useKnowledgeBase"));

            if (courseId == null || userMessage == null || userMessage.isEmpty()) {
                session.sendMessage(new TextMessage("[ERROR]参数错误"));
                return;
            }

            // 保存用户提问记录（仅保存 user 角色，用于统计）
            saveChatRecord(userId, courseId, userMessage);

            String systemPrompt = aiService.buildRAGPrompt(courseId, userId, userMessage, useKnowledgeBase);
            callLLMStream(systemPrompt, history, userMessage, session);

        } catch (Exception e) {
            log.error("Chat WS处理异常", e);
            session.sendMessage(new TextMessage("[ERROR]" + e.getMessage()));
        }
    }

    /**
     * 保存用户 AI 提问记录
     */
    private void saveChatRecord(Long userId, Long courseId, String content) {
        try {
            if (userId == null || courseId == null || content == null || content.trim().isEmpty()) {
                log.warn("WS保存 AI 聊天记录: 参数无效 userId={}, courseId={}", userId, courseId);
                return;
            }
            AiChatRecord record = new AiChatRecord();
            record.setUserId(userId);
            record.setCourseId(courseId);
            record.setRole("user");
            record.setContent(content.trim());
            record.setModel(llmModel);
            chatRecordMapper.insert(record);
            log.info("WS保存 AI 聊天记录成功: userId={}, courseId={}", userId, courseId);
        } catch (Exception e) {
            log.error("WS保存 AI 聊天记录失败: userId={}, courseId={}", userId, courseId, e);
        }
    }

    /**
     * 从 WebSocket URL 的 token 参数解析 userId
     */
    private Long resolveUserIdFromSession(WebSocketSession session) {
        try {
            String query = session.getUri() != null ? session.getUri().getQuery() : null;
            if (query == null || query.isEmpty()) {
                return null;
            }
            String token = null;
            for (String param : query.split("&")) {
                if (param.startsWith("token=")) {
                    token = param.substring(6);
                    break;
                }
            }
            if (token == null || token.isEmpty()) {
                return null;
            }
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
            return jwtUtil.getUserIdFromToken(token);
        } catch (Exception e) {
            log.warn("WS解析token失败", e);
            return null;
        }
    }

    /**
     * 流式调用LLM（OpenAI兼容格式 Chat Completions API, stream:true）
     */
    private void callLLMStream(String systemPrompt, List<Map<String, String>> history,
                                String userMessage, WebSocketSession session) {
        // 构建标准messages格式
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));

        if (history != null) {
            for (Map<String, String> h : history) {
                String content = h.get("content");
                if (content == null || content.trim().isEmpty()) continue;
                messages.add(Map.of("role", h.get("role"), "content", content));
            }
        }
        messages.add(Map.of("role", "user", "content", userMessage));

        // 构建请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", llmModel);
        requestBody.put("messages", messages);
        requestBody.put("stream", true);
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 4096);

        log.info("LLM流式请求: model={}, messages={}条", llmModel, messages.size());

        try {
            webClient.post()
                    .uri(llmApiUrl)
                    .header("Authorization", "Bearer " + llmApiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .exchangeToFlux(response -> {
                        if (response.statusCode().isError()) {
                            return response.bodyToMono(String.class)
                                    .flatMapMany(body -> {
                                        log.error("LLM流式API错误: {} - {}", response.statusCode(), body);
                                        return Flux.error(new RuntimeException("API Error: " + body));
                                    });
                        }
                        return response.body(BodyExtractors.toDataBuffers());
                    })
                    .map(buffer -> {
                        byte[] bytes = new byte[buffer.readableByteCount()];
                        buffer.read(bytes);
                        DataBufferUtils.release(buffer);
                        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                    })
                    .doOnNext(chunk -> processSSEChunk(chunk, session))
                    .doOnComplete(() -> sendToSession(session, "[DONE]"))
                    .doOnError(e -> {
                        log.error("LLM流式调用失败: {}", e.getMessage());
                        sendToSession(session, "[ERROR]AI服务暂时不可用");
                        sendToSession(session, "[DONE]");
                    })
                    .blockLast(Duration.ofSeconds(60));
        } catch (Exception e) {
            log.error("流式调用异常，降级为非流式", e);
            // 降级：非流式请求
            requestBody.remove("stream");
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> response = webClient.post()
                        .uri(llmApiUrl)
                        .header("Authorization", "Bearer " + llmApiKey)
                        .header("Content-Type", "application/json")
                        .bodyValue(requestBody)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block(Duration.ofSeconds(60));

                String text = extractFallbackText(response);
                session.sendMessage(new TextMessage(text));
                session.sendMessage(new TextMessage("[DONE]"));
            } catch (Exception e2) {
                log.error("降级调用也失败", e2);
                sendToSession(session, "抱歉，AI服务暂时不可用。");
                sendToSession(session, "[DONE]");
            }
        }
    }

    /**
     * 解析Chat Completions流式SSE事件(delta格式)
     * data: {"choices":[{"delta":{"content":"你好"},"index":0}]}
     */
    private void processSSEChunk(String chunk, WebSocketSession session) {
        try {
            for (String line : chunk.split("\n")) {
                String trimmed = line.trim();
                if (!trimmed.startsWith("data: ")) continue;
                String jsonStr = trimmed.substring(6).trim();
                if ("[DONE]".equals(jsonStr)) continue;

                @SuppressWarnings("unchecked")
                Map<String, Object> data = objectMapper.readValue(jsonStr, Map.class);
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) data.get("choices");
                if (choices == null || choices.isEmpty()) continue;

                @SuppressWarnings("unchecked")
                Map<String, Object> delta = (Map<String, Object>) choices.get(0).get("delta");
                if (delta == null) continue;

                String content = (String) delta.get("content");
                if (content != null && !content.isEmpty()) {
                    sendToSession(session, content);
                }
            }
        } catch (Exception e) {
            log.warn("SSE解析跳过: {} - chunk: {}", e.getMessage(),
                    chunk.length() > 200 ? chunk.substring(0, 200) : chunk);
        }
    }

    private String extractFallbackText(Map<String, Object> response) {
        if (response == null) return "AI服务暂不可用";
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices != null && !choices.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                if (message != null && message.containsKey("content")) {
                    return (String) message.get("content");
                }
            }
        } catch (Exception e) {
            log.warn("解析降级响应失败", e);
        }
        return "AI服务暂不可用";
    }

    private void sendToSession(WebSocketSession session, String text) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(text));
            }
        } catch (IOException e) {
            log.warn("发送消息失败", e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        log.info("Chat WS closed: {}, status: {}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("Chat WS transport error: {}", session.getId(), exception);
        sessions.remove(session.getId());
    }
}
