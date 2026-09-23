package com.coursenote.service;

import com.coursenote.entity.KnowledgeBase;
import com.coursenote.entity.KnowledgeDocument;

import java.util.List;
import java.util.Map;

public interface KnowledgeBaseService {
    /**
     * 获取或创建课程知识库
     */
    KnowledgeBase getOrCreateKnowledgeBase(Long courseId, Long userId);

    /**
     * 获取知识库详情
     */
    Map<String, Object> getKnowledgeBaseDetail(Long courseId, Long userId);

    /**
     * 向知识库导入资料
     */
    KnowledgeDocument importDocument(Long courseId, Long materialId, Long userId);

    /**
     * 从知识库搜索相关内容
     */
    List<Map<String, Object>> searchKnowledge(Long courseId, Long userId, String query, int topK);

    /**
     * 获取知识库文档列表
     */
    List<KnowledgeDocument> getDocuments(Long courseId, Long userId);

    /**
     * 校验用户是否有权访问指定课程的知识库（任课教师或本班学生），无权时抛出 BusinessException(403)
     */
    void checkCourseAccess(Long courseId, Long userId);
}
