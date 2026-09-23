package com.coursenote.service.impl;

import com.coursenote.dto.AIChatRequest;
import com.coursenote.dto.AISummarizeRequest;
import com.coursenote.entity.Course;
import com.coursenote.entity.CourseSession;
import com.coursenote.entity.AiChatRecord;
import com.coursenote.entity.KnowledgeDocument;
import com.coursenote.entity.Material;
import com.coursenote.mapper.AiChatRecordMapper;
import com.coursenote.mapper.CourseMapper;
import com.coursenote.mapper.CourseSessionMapper;
import com.coursenote.mapper.MaterialMapper;
import com.coursenote.service.AIService;
import com.coursenote.service.KnowledgeBaseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;
import java.time.Duration;
import java.util.*;

/**
 * AI服务实现
 * 统一使用OpenAI兼容接口(支持DeepSeek/通义千问/GLM-4等任意兼容模型)
 */
@Slf4j
@Service
public class AIServiceImpl implements AIService {

    @Value("${ai.llm.api-key}")
    private String llmApiKey;

    @Value("${ai.llm.api-url}")
    private String llmApiUrl;

    @Value("${ai.llm.model}")
    private String llmModel;

    @Value("${ai.vision.api-key}")
    private String visionApiKey;

    @Value("${ai.vision.api-url}")
    private String visionApiUrl;

    @Value("${ai.vision.model}")
    private String visionModel;

    @Autowired
    private CourseSessionMapper sessionMapper;

    @Autowired
    private MaterialMapper materialMapper;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private AiChatRecordMapper chatRecordMapper;

    private final WebClient webClient = WebClient.builder().build();

    // ==================== AI整理归纳 ====================

    @Override
    public String summarizeSessionMaterials(AISummarizeRequest request) {
        log.info("开始AI整理归纳: sessionId={}", request.getSessionId());

        CourseSession session = sessionMapper.findById(request.getSessionId());
        if (session == null) {
            log.warn("课段不存在: {}", request.getSessionId());
            return "";
        }

        List<Material> materials = materialMapper.findBySessionId(request.getSessionId());
        if (materials.isEmpty()) {
            log.info("课段无资料，跳过归纳");
            return "";
        }

        String context = buildSummarizeContext(session, materials);
        String summary = callLLMForSummary(context);

        session.setSummary(summary);
        session.setSummaryStatus(2);
        sessionMapper.updateById(session);

        log.info("AI整理归纳完成: sessionId={}", request.getSessionId());
        return summary;
    }

    private String buildSummarizeContext(CourseSession session, List<Material> materials) {
        StringBuilder sb = new StringBuilder();
        sb.append("【课段信息】\n");
        sb.append("日期：").append(session.getSessionDate()).append("\n");
        sb.append("课程ID：").append(session.getCourseId()).append("\n\n");

        sb.append("【课段资料汇总】\n");
        for (Material material : materials) {
            sb.append("--- ").append(material.getType()).append(" ---\n");
            sb.append("标题：").append(material.getTitle()).append("\n");
            if (material.getContent() != null && !material.getContent().isEmpty()) {
                sb.append("内容：").append(material.getContent()).append("\n");
            }
            if (material.getAiTags() != null && !material.getAiTags().isEmpty()) {
                sb.append("标签：").append(material.getAiTags()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("【任务】\n");
        sb.append("请根据以上课段资料，生成一份结构化的课堂总结，包含：\n");
        sb.append("1. 本节课核心知识点\n");
        sb.append("2. 重点内容提炼\n");
        sb.append("3. 难点分析\n");
        sb.append("4. 建议复习方向\n");
        sb.append("请使用Markdown格式输出。\n");

        return sb.toString();
    }

    private String callLLMForSummary(String context) {
        return callLLM(context, "你是一位专业的课程笔记整理助手。请根据提供的课段资料，" +
                "生成一份结构清晰、重点突出的课堂总结。");
    }

    // ==================== AI知识库对话 ====================

    @Override
    public String chatWithKnowledge(AIChatRequest request) {
        Long userId = request.getUserId();
        Long courseId = request.getCourseId();
        String message = request.getMessage();
        log.info("AI知识库对话: courseId={}, userId={}, message={}", courseId, userId, message);

        // 保存用户提问记录（用于教师端统计）
        saveChatRecord(userId, courseId, message);

        String systemPrompt = buildRAGPrompt(courseId, userId,
                message, request.getUseKnowledgeBase() != null && request.getUseKnowledgeBase());

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));

        if (request.getHistory() != null) {
            for (AIChatRequest.ChatMessage msg : request.getHistory()) {
                messages.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
            }
        }
        messages.add(Map.of("role", "user", "content", request.getMessage()));

        return callLLM(messages);
    }

    /**
     * 保存用户 AI 提问记录
     */
    private void saveChatRecord(Long userId, Long courseId, String content) {
        try {
            if (userId == null || courseId == null || content == null || content.trim().isEmpty()) {
                log.warn("保存 AI 聊天记录: 参数无效 userId={}, courseId={}", userId, courseId);
                return;
            }
            AiChatRecord record = new AiChatRecord();
            record.setUserId(userId);
            record.setCourseId(courseId);
            record.setRole("user");
            record.setContent(content.trim());
            record.setModel(llmModel);
            chatRecordMapper.insert(record);
            log.info("保存 AI 聊天记录成功: userId={}, courseId={}, content={}", userId, courseId, 
                    content.length() > 50 ? content.substring(0, 50) + "..." : content);
        } catch (Exception e) {
            log.error("保存 AI 聊天记录失败: userId={}, courseId={}", userId, courseId, e);
        }
    }

    // ==================== AI标签生成 ====================

    @Override
    public String generateTags(String content, String type) {
        if (content == null || content.isEmpty()) {
            return "[]";
        }

        String prompt = String.format(
                "请分析以下%s内容，生成3-5个关键词标签，以JSON数组格式返回：\n\n%s",
                type.equals("PHOTO") ? "课堂拍照笔记" : "课堂笔记",
                content
        );

        String result = callLLM(prompt, "你是一位专业的知识标签生成助手，只输出JSON数组，不要多余内容。");
        try {
            return result;
        } catch (Exception e) {
            return "[\"" + content.substring(0, Math.min(10, content.length())) + "\"]";
        }
    }

    // ==================== 图片分析（通过LLM视觉能力或base64描述） ====================

    @Override
    public String analyzeImage(String imageBase64) {
        log.info("调用豆包视觉模型分析图片");
        try {
            // 豆包 Responses API 格式：input_image + input_text
            List<Map<String, Object>> content = new ArrayList<>();

            Map<String, Object> imagePart = new HashMap<>();
            imagePart.put("type", "input_image");
            imagePart.put("image_url", "data:image/jpeg;base64," + imageBase64);
            content.add(imagePart);

            Map<String, Object> textPart = new HashMap<>();
            textPart.put("type", "input_text");
            textPart.put("text", "你是一个专业的课堂内容分析助手，擅长从图片中提取文字信息和理解图片内容。请详细描述这张图片中的内容，提取所有可见的文字信息。输出格式：\n【图片内容描述】\n【提取文字】\n【关键要点】");
            content.add(textPart);

            Map<String, Object> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", content);

            List<Map<String, Object>> input = new ArrayList<>();
            input.add(userMsg);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", visionModel);
            requestBody.put("input", input);

            log.info("豆包视觉请求: model={}, imageSize={}bytes", visionModel, imageBase64.length());

            Map<String, Object> response = webClient.post()
                    .uri(visionApiUrl)
                    .header("Authorization", "Bearer " + visionApiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(errorBody -> {
                                        log.error("豆包视觉API错误: 状态码={}, 响应体={}", clientResponse.statusCode(), errorBody);
                                        return Mono.error(new RuntimeException("豆包API返回" + clientResponse.statusCode()));
                                    }))
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(60));

            if (response != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> output = (List<Map<String, Object>>) response.get("output");
                if (output != null && !output.isEmpty()) {
                    for (Map<String, Object> msg : output) {
                        if (!"message".equals(msg.get("type"))) continue;
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> respContent = (List<Map<String, Object>>) msg.get("content");
                        if (respContent != null && !respContent.isEmpty()) {
                            for (Map<String, Object> part : respContent) {
                                if ("output_text".equals(part.get("type")) && part.containsKey("text")) {
                                    String result = (String) part.get("text");
                                    log.info("豆包视觉分析完成: 结果长度={}", result != null ? result.length() : 0);
                                    return result;
                                }
                            }
                        }
                    }
                }
            }
            log.warn("豆包视觉API返回异常: {}", response);
            return "【图片内容描述】\nAI分析暂不可用\n【提取文字】\n请稍后重试\n【关键要点】\n图片已保存";
        } catch (Exception e) {
            log.error("豆包视觉分析失败", e);
            return "【图片内容描述】\n（AI视觉服务暂时不可用：" + e.getMessage() + "）\n【提取文字】\n图片已保存到知识库，可手动编辑\n【关键要点】\n请稍后重试";
        }
    }

    @Override
    public Map<String, Object> processUploadToKnowledge(Long courseId, Long userId,
                                                         String fileContent, String fileName,
                                                         String fileType, String fileUrl) {
        log.info("处理上传内容到知识库: courseId={}, type={}, fileName={}", courseId, fileType, fileName);
        Map<String, Object> result = new HashMap<>();

        try {
            String title;
            String summary;
            String content;

            if ("image".equals(fileType) || "PHOTO".equals(fileType)) {
                String visionResult = analyzeImage(fileContent);
                Map<String, String> sections = parseImageAnalysis(visionResult);
                String extractedText = sections.getOrDefault("extractedText", "");
                String keyPoints = sections.getOrDefault("keyPoints", "");
                String description = sections.getOrDefault("description", "");

                // 用于检索的纯文本内容（提取文字 + 关键要点）
                StringBuilder contentBuilder = new StringBuilder();
                if (!extractedText.trim().isEmpty()) {
                    contentBuilder.append(extractedText.trim()).append("\n\n");
                }
                if (!keyPoints.trim().isEmpty()) {
                    contentBuilder.append("关键要点：\n").append(keyPoints.trim());
                }
                content = contentBuilder.toString().trim();
                if (content.isEmpty()) {
                    content = visionResult;
                }

                // 用于卡片展示的摘要（精简，避免列表页过长）
                StringBuilder summaryBuilder = new StringBuilder();
                if (!description.trim().isEmpty()) {
                    summaryBuilder.append(description.trim()).append("\n");
                }
                if (!extractedText.trim().isEmpty()) {
                    String snippet = extractedText.trim();
                    if (snippet.length() > 200) {
                        snippet = snippet.substring(0, 200) + "...";
                    }
                    summaryBuilder.append("提取文字：").append(snippet).append("\n");
                }
                if (!keyPoints.trim().isEmpty()) {
                    summaryBuilder.append("关键要点：").append(keyPoints.trim());
                }
                summary = summaryBuilder.toString().trim();
                if (summary.isEmpty()) {
                    summary = visionResult;
                }

                title = extractTitleFromSummary(visionResult);
                if (title == null || title.length() < 3 || title.contains("图片内容描述") || title.contains("】")) {
                    title = cleanFileName(fileName);
                }
            } else {
                title = cleanFileName(fileName);
                String ext = fileName != null && fileName.contains(".")
                        ? fileName.substring(fileName.lastIndexOf(".") + 1).toUpperCase() : "未知";
                summary = "文件类型：" + ext + "，已保存到知识库，可在知识库文档中查看。";
                content = summary;
            }

            result.put("title", title);
            result.put("summary", summary);
            result.put("content", content);
            result.put("status", "completed");
        } catch (Exception e) {
            log.error("处理上传内容失败", e);
            result.put("title", fileName != null ? fileName : "资料文件");
            result.put("summary", "AI处理暂不可用");
            result.put("content", "AI处理暂不可用");
            result.put("status", "failed");
        }
        return result;
    }

    private String cleanFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) return "图片资料";
        // 去掉UUID前缀（如 "uuid_原始名.png" → "原始名"）
        int underscore = fileName.indexOf('_');
        String name = underscore > 0 && underscore < fileName.length() - 1
                ? fileName.substring(underscore + 1) : fileName;
        // 去掉扩展名
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        return name.length() > 50 ? name.substring(0, 50) + "..." : name;
    }

    private String extractTitleFromSummary(String summary) {
        if (summary == null || summary.isEmpty()) return "资料文件";
        try {
            String extractSection = "";
            if (summary.contains("【提取文字】")) {
                int start = summary.indexOf("【提取文字】") + 6;
                int end = summary.indexOf("【", start);
                if (end < 0) end = summary.length();
                String textContent = summary.substring(start, end).trim();
                for (String line : textContent.split("\n")) {
                    String trimmed = line.replace("·", "").replace("●", "").trim();
                    if (!trimmed.isEmpty()) {
                        extractSection = trimmed;
                        break;
                    }
                }
            }
            if (!extractSection.isEmpty() && extractSection.length() <= 50) {
                return extractSection;
            }
            for (String line : summary.split("\n")) {
                String trimmed = line.replace("【", "").trim();
                if (!trimmed.isEmpty() && trimmed.length() > 4) {
                    return trimmed.length() > 30 ? trimmed.substring(0, 30) + "..." : trimmed;
                }
            }
        } catch (Exception e) {
            log.warn("提取标题失败", e);
        }
        return "资料文件";
    }

    /**
     * 解析豆包视觉模型返回的结构化文本
     */
    private Map<String, String> parseImageAnalysis(String text) {
        Map<String, String> result = new HashMap<>();
        result.put("description", extractImageSection(text, "【图片内容描述】"));
        result.put("extractedText", extractImageSection(text, "【提取文字】"));
        result.put("keyPoints", extractImageSection(text, "【关键要点】"));
        return result;
    }

    private String extractImageSection(String text, String sectionTitle) {
        if (text == null || !text.contains(sectionTitle)) {
            return "";
        }
        int start = text.indexOf(sectionTitle) + sectionTitle.length();
        int end = text.indexOf("【", start);
        if (end < 0) {
            end = text.length();
        }
        return text.substring(start, end).trim();
    }

    // ==================== LLM调用通用方法（OpenAI兼容接口） ====================

    @Override
    public Boolean importToKnowledgeBase(Long courseId, Long materialId, Long userId) {
        try {
            knowledgeBaseService.importDocument(courseId, materialId, userId);
            return true;
        } catch (Exception e) {
            log.error("导入知识库失败", e);
            return false;
        }
    }

    @Override
    public List<Map<String, Object>> searchKnowledge(Long courseId, Long userId, String query, int topK) {
        return knowledgeBaseService.searchKnowledge(courseId, userId, query, topK);
    }

    @Override
    public String buildRAGPrompt(Long courseId, Long userId, String query, boolean useKnowledgeBase) {
        StringBuilder sb = new StringBuilder();

        Course course = courseMapper.selectById(courseId);
        String courseName = course != null ? course.getName() : "本课程";
        String courseDesc = course != null && course.getDescription() != null ? course.getDescription() : "";

        sb.append("你是一位课程学习助手，正在协助学生学习《").append(courseName).append("》课程。\n");
        if (!courseDesc.isEmpty()) {
            sb.append("课程简介：").append(courseDesc).append("\n");
        }
        sb.append("\n以下是知识库内容：\n\n");

        if (useKnowledgeBase && courseId != null) {
            List<Map<String, Object>> relevantDocs = knowledgeBaseService.searchKnowledge(courseId, userId, query, 5);

            if (relevantDocs != null && !relevantDocs.isEmpty()) {
                sb.append("【相关资料】\n");
                for (int i = 0; i < relevantDocs.size(); i++) {
                    Map<String, Object> doc = relevantDocs.get(i);
                    sb.append(i + 1).append(". 【").append(doc.get("title")).append("】\n");
                    if (doc.get("content") != null) {
                        sb.append(doc.get("content")).append("\n\n");
                    }
                }
            } else {
                sb.append("未找到直接匹配的内容。当前知识库文档列表：\n\n");
                try {
                    List<KnowledgeDocument> allDocs = knowledgeBaseService.getDocuments(courseId, userId);
                    if (allDocs != null && !allDocs.isEmpty()) {
                        for (int i = 0; i < allDocs.size(); i++) {
                            KnowledgeDocument doc = allDocs.get(i);
                            sb.append(i + 1).append(". ").append(doc.getTitle()).append("\n");
                            if (doc.getSummary() != null && !doc.getSummary().isEmpty()) {
                                sb.append("   摘要：").append(doc.getSummary()).append("\n");
                            }
                        }
                    } else {
                        sb.append("（知识库暂无文档）\n");
                    }
                } catch (Exception e) {
                    sb.append("（知识库暂无文档）\n");
                }
            }
        }

        sb.append("\n## 回答规则（必须严格遵守）\n");
        sb.append("1. 优先基于上面提供的知识库内容回答用户问题；如果知识库中有直接相关的资料，请充分引用。\n");
        sb.append("2. 如果用户问题中提到“这个”“这张图”“刚才上传的照片”“最新上传”等指代，默认结合最近列出的文档（通常是第一条）回答，不要随意判定为不相关。\n");
        sb.append("3. 如果用户的问题与《").append(courseName).append("》课程主题明显无关（如闲聊、政治、娱乐、代码调试等），回复：\"抱歉，我仅能回答与《").append(courseName).append("》知识库相关的问题。请问有什么课程内容需要帮助的吗？\"\n");
        sb.append("4. 教育类请求（出题、练习、总结、重点、考点、分析等）即使知识库中缺少直接对应内容，也应视为课程相关问题，可结合已有资料合理生成。\n");
        sb.append("5. 如果知识库中没有与用户问题直接相关的内容，列出已有文档标题并引导用户补充描述或选择具体资料，不要直接说“不相关”。\n");
        sb.append("6. 可以根据知识库内容进行合理延伸和总结，但不得编造与知识库无关的信息。\n");
        sb.append("7. 回复格式要求：使用清晰的排版，段落之间用空行分隔，列表条目用换行和缩进展示。\n");
        sb.append("8. 回复时不要以“根据知识库内容”“基于以上资料”等类似表达作为开头，直接给出答案内容。\n");

        return sb.toString();
    }

    /**
     * 简单的LLM调用
     */
    private String callLLM(String content, String systemPrompt) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", content));
        return callLLM(messages);
    }

    /**
     * LLM调用核心方法（OpenAI兼容接口）
     * 支持 DeepSeek / 通义千问 / GLM-4 / 混元 等所有兼容OpenAI格式的模型
     */
    private String callLLM(List<Map<String, String>> messages) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", llmModel);
            requestBody.put("messages", messages);
            requestBody.put("temperature", 0.7);
            requestBody.put("max_tokens", 4096);

            log.info("LLM调用: model={}, messages={}条", llmModel, messages.size());

            Map<String, Object> response = webClient.post()
                    .uri(llmApiUrl)
                    .header("Authorization", "Bearer " + llmApiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(errorBody -> {
                                        log.error("LLM API错误: 状态码={}, 响应体={}", 
                                                clientResponse.statusCode(), errorBody);
                                        return Mono.error(new RuntimeException(
                                                "LLM API返回" + clientResponse.statusCode() + ": " + errorBody));
                                    }))
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(60));

            if (response != null && response.containsKey("choices")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    String content = (String) message.get("content");
                    log.info("LLM返回: 长度={}", content != null ? content.length() : 0);
                    return content;
                }
            }

            log.warn("LLM调用返回异常: {}", response);
            return "AI处理暂不可用，请稍后重试。";
        } catch (Exception e) {
            log.error("LLM调用失败", e);
            return fallbackSummary(messages);
        }
    }

    private String fallbackSummary(List<Map<String, String>> messages) {
        return "## 课堂总结\n\n" +
               "### 核心知识点\n" +
               "本节课主要涵盖了以下内容（AI总结功能需配置API Key后使用）：\n\n" +
               "1. **重点概念**\n" +
               "2. **关键公式/原理**\n" +
               "3. **实践应用**\n\n" +
               "---\n" +
               "*提示：请在application.yml中配置 ai.llm.api-key 以启用完整的AI功能。*";
    }
}
