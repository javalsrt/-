package com.coursenote.service;

import com.coursenote.dto.AIChatRequest;
import com.coursenote.dto.AISummarizeRequest;

import java.util.List;
import java.util.Map;

public interface AIService {
    /**
     * AI整理归纳课段资料
     */
    String summarizeSessionMaterials(AISummarizeRequest request);

    /**
     * AI对课程知识库提问
     */
    String chatWithKnowledge(AIChatRequest request);

    /**
     * 为资料生成AI标签
     */
    String generateTags(String content, String type);

    /**
     * 将资料内容导入知识库
     */
    Boolean importToKnowledgeBase(Long courseId, Long materialId, Long userId);

    /**
     * 使用豆包视觉模型分析图片，返回结构化摘要（含OCR文字+场景描述）
     */
    String analyzeImage(String imageBase64);

    /**
     * 使用豆包处理上传内容并导入知识库，返回生成的摘要标题
     */
    Map<String, Object> processUploadToKnowledge(Long courseId, Long userId,
                                                  String fileContent, String fileName,
                                                  String fileType, String fileUrl);

    /**
     * 检索知识库文档（关键词匹配）
     */
    List<Map<String, Object>> searchKnowledge(Long courseId, Long userId, String query, int topK);

    /**
     * 构建RAG提示词
     */
    String buildRAGPrompt(Long courseId, Long userId, String query, boolean useKnowledgeBase);
}
