package com.coursenote.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.coursenote.common.BusinessException;
import com.coursenote.entity.*;
import com.coursenote.mapper.*;
import com.coursenote.service.KnowledgeBaseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 知识库服务实现
 *
 * 核心创新: 以课程为维度建立AI知识库
 * 课段内的所有资料（拍照OCR、笔记、导入材料）
 * 自动归类到对应课程的知识库中，支持RAG问答
 */
@Slf4j
@Service
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Autowired
    private KnowledgeDocumentMapper documentMapper;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private MaterialMapper materialMapper;

    @Autowired
    private UserMapper userMapper;

    @Override
    @Transactional
    public KnowledgeBase getOrCreateKnowledgeBase(Long courseId, Long userId) {
        KnowledgeBase kb = knowledgeBaseMapper.findByCourseIdAndUserId(courseId, userId);

        if (kb == null) {
            try {
                Course course = courseMapper.selectById(courseId);
                String courseName = "课程";
                if (course != null) {
                    courseName = course.getName();
                } else {
                    log.warn("课程不存在: courseId={}, 使用默认名称创建知识库", courseId);
                }

                kb = new KnowledgeBase();
                kb.setCourseId(courseId);
                kb.setUserId(userId);
                kb.setName(courseName + "知识库");
                kb.setDescription(courseName + "的AI课程知识库，包含拍照笔记、文字笔记等资料");
                kb.setDocumentCount(0);

                knowledgeBaseMapper.insert(kb);
                log.info("创建课程知识库: courseId={}, userId={}, kbName={}", courseId, userId, kb.getName());
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // 并发创建时，另一个请求已插入成功，重新查询
                log.warn("知识库并发创建冲突，重新查询: courseId={}", courseId);
                kb = knowledgeBaseMapper.findByCourseIdAndUserId(courseId, userId);
                if (kb == null) throw e;
            }
        }

        return kb;
    }

    @Override
    public Map<String, Object> getKnowledgeBaseDetail(Long courseId, Long userId) {
        checkCourseAccess(courseId, userId);
        // 保留学生个人知识库（用于后续上传）
        KnowledgeBase kb = knowledgeBaseMapper.findByCourseIdAndUserId(courseId, userId);
        if (kb == null) {
            kb = getOrCreateKnowledgeBase(courseId, userId);
        }
        Course course = courseMapper.selectById(courseId);

        // 聚合当前学生在该课程下的个人知识库文档
        List<KnowledgeDocument> allCourseDocs = getCourseDocuments(courseId, userId);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("knowledgeBase", kb);
        detail.put("course", course);
        detail.put("documents", allCourseDocs);
        detail.put("totalDocs", allCourseDocs.size());
        detail.put("indexedDocs", (int) allCourseDocs.stream().filter(d -> d.getIndexStatus() == 2).count());

        return detail;
    }

    /**
     * 获取与指定课程"同名同班"的所有课程ID
     * 解决同一门课因不同上课时间产生多个 course_id 的问题
     */
    private List<Long> getRelatedCourseIds(Long courseId) {
        Course course = courseMapper.selectById(courseId);
        if (course == null || course.getName() == null) {
            return Collections.singletonList(courseId);
        }
        LambdaQueryWrapper<Course> w = new LambdaQueryWrapper<Course>()
                .eq(Course::getName, course.getName())
                .eq(Course::getDeleted, 0);
        if (course.getDescription() != null && !course.getDescription().isEmpty()) {
            w.eq(Course::getDescription, course.getDescription());
        }
        List<Course> related = courseMapper.selectList(w);
        if (related == null || related.isEmpty()) {
            return Collections.singletonList(courseId);
        }
        return related.stream()
                .map(Course::getId)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 校验当前用户是否有权访问指定课程的知识库
     * 规则：课程的任课教师（course.user_id）本人，
     *      或班级（user.grade）与课程 description 精确匹配的学生
     */
    @Override
    public void checkCourseAccess(Long courseId, Long userId) {
        if (courseId == null || userId == null) {
            throw new BusinessException(403, "无权访问该课程知识库");
        }
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        // 任课教师本人
        if (course.getUserId() != null && course.getUserId().equals(userId)) {
            return;
        }
        // 学生：班级精确匹配（与课表匹配规则一致）
        User user = userMapper.selectById(userId);
        String className = course.getDescription();
        if (user != null && className != null && !className.isEmpty()
                && className.equals(user.getGrade())) {
            return;
        }
        // 课程未设置班级时，保持原有放开策略（演示环境兼容）
        if (className == null || className.isEmpty()) {
            return;
        }
        throw new BusinessException(403, "无权访问该课程知识库");
    }

    /**
     * 聚合指定课程（含同名同班的所有课程）下当前用户可见的知识库文档
     * 可见范围 = 学生本人上传的文档 + 任课教师上传到课程知识库的文档
     * （其他同学的个人文档保持隔离，教师文档为课程共享资料）
     */
    private List<KnowledgeDocument> getCourseDocuments(Long courseId, Long userId) {
        List<Long> courseIds = getRelatedCourseIds(courseId);

        // 收集这些课程的任课教师ID（教师上传的文档对全班共享）
        Set<Long> teacherIds = new HashSet<>();
        for (Long cid : courseIds) {
            Course c = courseMapper.selectById(cid);
            if (c != null && c.getUserId() != null) {
                teacherIds.add(c.getUserId());
            }
        }

        // 聚合这些课程下的所有知识库（含教师与学生的知识库）
        Set<Long> kbIds = new LinkedHashSet<>();
        for (Long cid : courseIds) {
            List<KnowledgeBase> kbs = knowledgeBaseMapper.findByCourseId(cid);
            if (kbs != null) {
                for (KnowledgeBase kb : kbs) {
                    kbIds.add(kb.getId());
                }
            }
        }
        if (kbIds.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> docIds = new LinkedHashSet<>();
        List<KnowledgeDocument> result = new ArrayList<>();
        for (Long kbId : kbIds) {
            List<KnowledgeDocument> docs = documentMapper.findByKnowledgeBaseId(kbId);
            for (KnowledgeDocument doc : docs) {
                Long ownerId = doc.getUserId();
                boolean visible = (ownerId != null && ownerId.equals(userId))
                        || (ownerId != null && teacherIds.contains(ownerId));
                if (visible && docIds.add(doc.getId())) {
                    result.add(doc);
                }
            }
        }
        // 按创建时间倒序排列
        result.sort((a, b) -> {
            if (a.getCreatedAt() == null || b.getCreatedAt() == null) return 0;
            return b.getCreatedAt().compareTo(a.getCreatedAt());
        });
        return result;
    }

    @Override
    @Transactional
    public KnowledgeDocument importDocument(Long courseId, Long materialId, Long userId) {
        // 0. 权限校验：仅本课程教师或本班学生可导入
        checkCourseAccess(courseId, userId);

        // 1. 获取或创建知识库
        KnowledgeBase kb = getOrCreateKnowledgeBase(courseId, userId);

        // 2. 检查当前学生是否已导入该资料
        KnowledgeDocument existing = documentMapper.findByMaterialIdAndUserId(materialId, userId);
        if (existing != null) {
            log.info("资料已导入知识库: materialId={}, userId={}, docId={}", materialId, userId, existing.getId());
            return existing;
        }

        // 3. 获取资料内容
        Material material = materialMapper.findById(materialId);
        if (material == null) {
            throw new BusinessException("资料不存在");
        }

        // 4. 构建文档内容
        String docContent = buildDocumentContent(material);

        // 5. 创建知识库文档
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setKnowledgeBaseId(kb.getId());
        doc.setMaterialId(materialId);
        doc.setUserId(userId);
        doc.setTitle(material.getTitle() != null ? material.getTitle() : "无标题");
        doc.setContent(docContent);
        doc.setSummary(material.getAiSummary());
        doc.setFileUrl(material.getFileUrl());
        doc.setIndexStatus(0); // 待索引

        documentMapper.insert(doc);

        // 6. 更新知识库文档计数
        int count = documentMapper.findByKnowledgeBaseId(kb.getId()).size();
        kb.setDocumentCount(count);
        knowledgeBaseMapper.updateById(kb);

        // 7. 异步生成向量嵌入（在实际项目中，调用向量化服务）
        generateVectorEmbeddingAsync(doc);

        log.info("资料导入知识库成功: materialId={}, docId={}, kbId={}",
                materialId, doc.getId(), kb.getId());

        return doc;
    }

    /**
     * 根据资料类型构建知识库文档内容
     */
    private String buildDocumentContent(Material material) {
        StringBuilder sb = new StringBuilder();

        sb.append("标题：").append(material.getTitle()).append("\n");
        sb.append("类型：").append(materialTypeToChinese(material.getType())).append("\n");
        sb.append("创建时间：").append(material.getCreatedAt()).append("\n");

        if (material.getContent() != null && !material.getContent().isEmpty()) {
            sb.append("\n【内容】\n").append(material.getContent()).append("\n");
        }

        if (material.getAiTags() != null && !material.getAiTags().isEmpty()) {
            sb.append("\n【标签】\n").append(material.getAiTags()).append("\n");
        }

        if (material.getDescription() != null && !material.getDescription().isEmpty()) {
            sb.append("\n【描述】\n").append(material.getDescription()).append("\n");
        }

        return sb.toString();
    }

    private String materialTypeToChinese(String type) {
        return switch (type) {
            case "PHOTO" -> "拍照笔记";
            case "NOTE" -> "文字笔记";
            case "THIRD_PARTY" -> "导入资料";
            default -> type;
        };
    }

    /**
     * 异步生成向量嵌入
     * 在实际项目中，此处调用向量化API（如OpenAI Embeddings或腾讯云向量数据库）
     * 将文档分块后生成向量，存入向量数据库用于语义检索
     */
    private void generateVectorEmbeddingAsync(KnowledgeDocument doc) {
        try {
            // 实际实现:
            // 1. 将文档内容分块
            // 2. 调用 Embedding API 为每块生成向量
            // 3. 存入向量数据库（如 Milvus / Tencent VectorDB）
            // 4. 更新索引状态

            // 更新索引状态为已索引（模拟）
            doc.setIndexStatus(2);
            doc.setChunkCount((int) Math.ceil(doc.getContent().length() / 500.0));
            documentMapper.updateById(doc);

            log.info("知识库文档向量化完成: docId={}", doc.getId());
        } catch (Exception e) {
            log.error("知识库文档向量化失败: docId={}", doc.getId(), e);
            doc.setIndexStatus(3);
            documentMapper.updateById(doc);
        }
    }

    @Override
    public List<Map<String, Object>> searchKnowledge(Long courseId, Long userId, String query, int topK) {
        // 权限校验：无权访问时返回空结果（问答场景降级为通用回答，不抛异常打断对话）
        try {
            checkCourseAccess(courseId, userId);
        } catch (BusinessException e) {
            log.warn("知识库检索越权拦截: courseId={}, userId={}", courseId, userId);
            return Collections.emptyList();
        }
        // 聚合当前用户在该课程下可见的知识库文档（本人 + 任课教师共享）
        List<KnowledgeDocument> allDocs = getCourseDocuments(courseId, userId);
        if (allDocs.isEmpty()) {
            return Collections.emptyList();
        }

        String queryLower = query != null ? query.toLowerCase() : "";
        List<String> keywords = extractKeywords(query);
        boolean vagueReference = isVagueReference(queryLower);

        List<Map<String, Object>> results = new ArrayList<>();
        for (KnowledgeDocument doc : allDocs) {
            if (doc.getIndexStatus() == null || doc.getIndexStatus() != 2) continue;

            String title = doc.getTitle() != null ? doc.getTitle().toLowerCase() : "";
            String content = doc.getContent() != null ? doc.getContent().toLowerCase() : "";
            String summary = doc.getSummary() != null ? doc.getSummary().toLowerCase() : "";

            double score = 0;
            // 完整查询串匹配（加分最高）
            if (!queryLower.isEmpty()) {
                if (title.contains(queryLower)) score += 20;
                if (content.contains(queryLower)) score += 15;
                if (summary.contains(queryLower)) score += 10;
            }
            // 关键词分散匹配
            for (String kw : keywords) {
                if (title.contains(kw)) score += 5;
                if (content.contains(kw)) score += 3;
                if (summary.contains(kw)) score += 1;
            }
            // 模糊指代（这个、这张图、刚上传等）增加新近度权重
            if (vagueReference && doc.getCreatedAt() != null) {
                long hoursAgo = Duration.between(doc.getCreatedAt(), LocalDateTime.now()).toHours();
                score += Math.max(0, 10 - hoursAgo / 6.0);
            }

            if (score > 0) {
                results.add(buildSearchResult(doc, score));
            }
        }

        // 模糊指代且未命中关键词时，兜底返回最近上传的文档
        if (results.isEmpty() && vagueReference) {
            for (KnowledgeDocument doc : allDocs) {
                if (doc.getIndexStatus() == null || doc.getIndexStatus() != 2) continue;
                results.add(buildSearchResult(doc, 0.5));
                if (results.size() >= topK) break;
            }
        }

        // 按相关度倒序，相关度相同按创建时间倒序
        results.sort((a, b) -> {
            int cmp = Double.compare((Double) b.get("relevance"), (Double) a.get("relevance"));
            if (cmp != 0) return cmp;
            LocalDateTime da = (LocalDateTime) a.get("createdAt");
            LocalDateTime db = (LocalDateTime) b.get("createdAt");
            if (da == null || db == null) return 0;
            return db.compareTo(da);
        });

        return results.stream().limit(topK).collect(Collectors.toList());
    }

    private Map<String, Object> buildSearchResult(KnowledgeDocument doc, double score) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", doc.getId());
        item.put("title", doc.getTitle());
        String snippet = doc.getContent();
        if (snippet != null && snippet.length() > 3000) {
            snippet = snippet.substring(0, 3000) + "...";
        }
        item.put("content", snippet);
        item.put("relevance", Math.min(0.99, score / 25.0));
        item.put("createdAt", doc.getCreatedAt());
        return item;
    }

    private List<String> extractKeywords(String query) {
        if (query == null) {
            return Collections.emptyList();
        }
        String q = query.toLowerCase();
        Set<String> stopWords = new HashSet<>(Arrays.asList(
                "的", "了", "是", "在", "我", "有", "和", "就", "不", "人", "都", "一", "一个", "上", "也", "很",
                "到", "说", "要", "去", "你", "会", "着", "没有", "看", "好", "自己", "这", "那", "这个", "那个",
                "什么", "怎么", "为什么", "哪些", "吗", "呢", "吧", "啊", "嗯", "图", "张", "照片", "图片", "上传",
                "刚才", "刚刚", "最新", "最近", "一下", "一些", "可以", "还是", "或者", "与", "及", "等", "请",
                "帮忙", "解释", "给", "讲", "下"
        ));
        List<String> keywords = new ArrayList<>();
        // 英文/数字词（>=2字符）
        Pattern wordPattern = Pattern.compile("[a-z0-9]{2,}");
        Matcher wm = wordPattern.matcher(q);
        while (wm.find()) {
            String kw = wm.group();
            if (!stopWords.contains(kw)) keywords.add(kw);
        }
        // 中文字符
        Pattern cjkPattern = Pattern.compile("[\\u4e00-\\u9fa5]");
        Matcher cm = cjkPattern.matcher(q);
        while (cm.find()) {
            String kw = cm.group();
            if (!stopWords.contains(kw)) keywords.add(kw);
        }
        return keywords;
    }

    private boolean isVagueReference(String query) {
        if (query == null) return false;
        String[] refs = {"这个", "那个", "这张图", "那张图", "这张照片", "那张照片",
                "这个文件", "刚才", "刚刚", "最新", "最近", "上传", "传了"};
        for (String r : refs) {
            if (query.contains(r)) return true;
        }
        return false;
    }

    @Override
    public List<KnowledgeDocument> getDocuments(Long courseId, Long userId) {
        checkCourseAccess(courseId, userId);
        // 学生个人知识库不存在时自动创建，保证后续上传有归属
        KnowledgeBase kb = knowledgeBaseMapper.findByCourseIdAndUserId(courseId, userId);
        if (kb == null) {
            getOrCreateKnowledgeBase(courseId, userId);
        }
        // 聚合当前用户在该课程下可见的知识库文档（本人 + 任课教师共享）
        return getCourseDocuments(courseId, userId);
    }
}
