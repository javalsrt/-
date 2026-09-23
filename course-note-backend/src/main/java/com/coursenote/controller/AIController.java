package com.coursenote.controller;

import com.coursenote.common.Result;
import com.coursenote.dto.AIChatRequest;
import com.coursenote.dto.AISummarizeRequest;
import com.coursenote.entity.Course;
import com.coursenote.entity.KnowledgeBase;
import com.coursenote.entity.KnowledgeDocument;
import com.coursenote.entity.Material;
import com.coursenote.mapper.CourseMapper;
import com.coursenote.mapper.KnowledgeBaseMapper;
import com.coursenote.mapper.KnowledgeDocumentMapper;
import com.coursenote.mapper.MaterialMapper;
import com.coursenote.service.AIService;
import com.coursenote.service.KnowledgeBaseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/ai")
public class AIController {

    @Autowired
    private AIService aiService;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Autowired
    private KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Autowired
    private MaterialMapper materialMapper;

    @Autowired
    private CourseMapper courseMapper;

    @Value("${file.upload-dir}")
    private String uploadDir;

    /**
     * AI整理归纳课段资料
     * 创新点：以课段为单位，将拍照、笔记、导入材料统一归纳
     */
    @PostMapping("/summarize")
    public Result<String> summarizeSession(@RequestBody AISummarizeRequest request) {
        String summary = aiService.summarizeSessionMaterials(request);
        return Result.success(summary);
    }

    /**
     * AI知识库对话
     * 创新点：以课程知识库为上下文进行问答
     */
    @PostMapping("/chat")
    public Result<String> chatWithKnowledge(@RequestAttribute Long userId, @RequestBody AIChatRequest request) {
        request.setUserId(userId);
        String reply = aiService.chatWithKnowledge(request);
        return Result.success(reply);
    }

    /**
     * 向知识库导入资料
     */
    @PostMapping("/knowledge/import")
    public Result<Boolean> importToKnowledgeBase(
            @RequestAttribute Long userId,
            @RequestParam Long courseId,
            @RequestParam Long materialId) {
        return Result.success(aiService.importToKnowledgeBase(courseId, materialId, userId));
    }

    /**
     * 获取课程知识库详情
     */
    @GetMapping("/knowledge/{courseId}")
    public Result<Map<String, Object>> getKnowledgeBase(
            @RequestAttribute Long userId,
            @PathVariable Long courseId) {
        return Result.success(knowledgeBaseService.getKnowledgeBaseDetail(courseId, userId));
    }

    /**
     * 知识库文档列表
     */
    @GetMapping("/knowledge/{courseId}/documents")
    public Result<?> getKnowledgeDocuments(
            @RequestAttribute Long userId,
            @PathVariable Long courseId) {
        return Result.success(knowledgeBaseService.getDocuments(courseId, userId));
    }

    /**
     * 上传资料到课程知识库（图片/文件）
     * 使用豆包AI自动识别内容并生成摘要
     */
    @PostMapping("/knowledge/upload")
    public Result<Map<String, Object>> uploadToKnowledge(
            @RequestAttribute Long userId,
            @RequestParam Long courseId,
            @RequestParam(required = false) String fileType,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String fileName,
            @RequestParam(required = false) String title) {
        try {
            // 权限校验：仅任课教师或本班学生可上传到该课程知识库
            knowledgeBaseService.checkCourseAccess(courseId, userId);

            // 优先使用前端传来的原始文件名，回退到 MultipartFile 的原始名
            String originalName = fileName != null && !fileName.isEmpty() ? fileName : file.getOriginalFilename();

            // fileType 非必填：未传时按文件扩展名/ContentType 推断（image 或 file）
            if (fileType == null || fileType.isEmpty()) {
                fileType = inferFileType(originalName, file.getContentType());
            }
            log.info("知识库上传: courseId={}, type={}, fileName={}", courseId, fileType, originalName);

            // 1. 保存文件到统一配置目录
            String dateDir = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            Path rootDir = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path saveDir = rootDir.resolve("knowledge").resolve(dateDir).normalize();
            Files.createDirectories(saveDir);
            log.info("文件保存目录: {}", saveDir);

            String extension = fileExtension(originalName);
            String saveName = UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);
            Path savePath = saveDir.resolve(saveName).normalize();
            if (!savePath.startsWith(rootDir)) {
                return Result.error(400, "非法文件路径");
            }
            file.transferTo(savePath);
            File saveFile = savePath.toFile();
            log.info("文件保存成功: {}", saveFile.getAbsolutePath());
            String fileUrl = "/uploads/knowledge/" + dateDir + "/" + saveName;

            // 2. 读取内容并调用AI处理
            String fileContent;
            if ("image".equals(fileType)) {
                // 图片：转base64
                byte[] bytes = Files.readAllBytes(saveFile.toPath());
                fileContent = Base64.getEncoder().encodeToString(bytes);
            } else if (isTextFile(originalName)) {
                // 纯文本文件（txt/md 等）：读取真实文本内容，供检索与问答使用
                String text = Files.readString(saveFile.toPath(), java.nio.charset.StandardCharsets.UTF_8);
                fileContent = text.length() > 20000 ? text.substring(0, 20000) : text;
            } else {
                // 其他二进制文件暂存文件名
                fileContent = originalName != null ? originalName : "unknown";
            }

            Map<String, Object> aiResult = aiService.processUploadToKnowledge(
                    courseId, userId, fileContent, originalName, fileType, fileUrl);

            // 前端显式传入的标题优先，其次AI识别标题，最后回退文件名
            String aiTitle = (String) aiResult.get("title");
            String docTitle = title != null && !title.isEmpty() ? title
                    : (aiTitle != null && !aiTitle.isEmpty() ? aiTitle
                    : (originalName != null ? originalName : "资料文件"));
            String docSummary = (String) aiResult.getOrDefault("summary", "");
            String docContent = (String) aiResult.getOrDefault("content", docSummary);
            // 纯文本文件以真实文本作为文档内容，保证可检索
            if (!"image".equals(fileType) && isTextFile(originalName) && fileContent != null && !fileContent.isEmpty()) {
                docContent = fileContent;
            }

            // 3. 同时写入 material 表（教师端资料管理需要）
            Material material = new Material();
            material.setSessionId(0L);  // 知识库上传不关联课段
            material.setUserId(userId);
            material.setCourseId(courseId);
            material.setType("image".equals(fileType) ? "PHOTO" : "THIRD_PARTY");
            material.setTitle(docTitle);
            material.setContent(docContent);
            material.setAiSummary(docSummary);
            material.setFileUrl(fileUrl);
            material.setAiTags("");
            material.setAiProcessed(1);
            material.setSource("self");
            material.setCreatedAt(LocalDateTime.now());
            material.setUpdatedAt(LocalDateTime.now());
            materialMapper.insert(material);

            // 4. 确保该用户的课程知识库存在（统一走Service，保证命名规范与并发安全）
            KnowledgeBase kb = knowledgeBaseService.getOrCreateKnowledgeBase(courseId, userId);

            // 5. 创建知识库文档（记录userId实现学生隔离）
            KnowledgeDocument doc = new KnowledgeDocument();
            doc.setKnowledgeBaseId(kb.getId());
            doc.setMaterialId(material.getId());
            doc.setUserId(userId);  // 学生隔离关键字段
            doc.setTitle(docTitle);
            doc.setSummary(docSummary);
            doc.setContent(docContent);
            doc.setFileUrl(fileUrl);
            doc.setIndexStatus(2);
            doc.setChunkCount(docContent != null ? (int) Math.ceil(docContent.length() / 500.0) : 0);
            knowledgeDocumentMapper.insert(doc);

            // 6. 更新知识库文档数
            kb.setDocumentCount(knowledgeDocumentMapper.findByKnowledgeBaseId(kb.getId()).size());
            knowledgeBaseMapper.updateById(kb);

            // 7. 返回卡片数据
            Map<String, Object> card = new HashMap<>();
            card.put("id", doc.getId());
            card.put("materialId", material.getId());
            card.put("title", doc.getTitle());
            card.put("summary", doc.getSummary());
            card.put("fileUrl", fileUrl);
            card.put("fileType", fileType);
            card.put("_type", fileType);
            card.put("createdAt", doc.getCreatedAt() != null ?
                    doc.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) :
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            card.put("indexStatus", doc.getIndexStatus());

            log.info("知识库上传完成: docId={}, materialId={}, title={}", doc.getId(), material.getId(), doc.getTitle());
            return Result.success(card);
        } catch (Exception e) {
            log.error("知识库上传失败", e);
            return Result.error("上传失败: " + e.getMessage());
        }
    }

    /**
     * 按文件名/ContentType推断上传类型（image 或 file）
     */
    private String inferFileType(String fileName, String contentType) {
        if (contentType != null && contentType.startsWith("image/")) {
            return "image";
        }
        if (fileName != null) {
            String ext = fileName.contains(".")
                    ? fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase() : "";
            if (Arrays.asList("jpg", "jpeg", "png", "gif", "bmp", "webp").contains(ext)) {
                return "image";
            }
        }
        return "file";
    }

    /**
     * 判断是否为可直接读取文本的文件（txt/md 等纯文本格式）
     */
    private boolean isTextFile(String fileName) {
        if (fileName == null) return false;
        String ext = fileExtension(fileName);
        return Arrays.asList("txt", "md", "markdown", "text", "log", "csv", "json").contains(ext);
    }

    private String fileExtension(String fileName) {
        if (fileName == null) return "";
        int index = fileName.lastIndexOf('.');
        return index < 1 || index == fileName.length() - 1
                ? "" : fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 判断当前用户是否有权操作该文档（文档所有者或所属课程的教师）
     */
    private boolean hasDocPermission(KnowledgeDocument doc, Long userId) {
        if (doc.getUserId() != null && doc.getUserId().equals(userId)) {
            return true;
        }
        // 教师权限：检查当前用户是否是文档所属课程的教师
        KnowledgeBase kb = knowledgeBaseMapper.selectById(doc.getKnowledgeBaseId());
        if (kb != null && kb.getCourseId() != null) {
            Course course = courseMapper.selectById(kb.getCourseId());
            if (course != null && course.getUserId() != null && course.getUserId().equals(userId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 删除知识库文档（同时清理关联 material 和磁盘文件）
     */
    @DeleteMapping("/knowledge/document/{id}")
    public Result<Boolean> deleteDocument(
            @RequestAttribute Long userId,
            @PathVariable Long id) {
        try {
            KnowledgeDocument doc = knowledgeDocumentMapper.selectById(id);
            if (doc == null) {
                return Result.error("文档不存在");
            }
            if (!hasDocPermission(doc, userId)) {
                return Result.error("无权删除他人文档");
            }

            // 1. 删除关联的 material（仅限知识库直接上传产生的副本，即 sessionId=0 的记录；
            //    从课段资料导入的文档不得连带删除学生的原始笔记/照片）
            if (doc.getMaterialId() != null) {
                try {
                    Material linked = materialMapper.findById(doc.getMaterialId());
                    if (linked != null && linked.getSessionId() != null && linked.getSessionId() == 0L) {
                        materialMapper.deleteById(doc.getMaterialId());
                    }
                } catch (Exception e) {
                    log.warn("删除关联 material 失败: materialId={}", doc.getMaterialId(), e);
                }
            }

            // 2. 删除磁盘文件（仅限统一上传目录，防止误删）
            String fileUrl = doc.getFileUrl();
            if (fileUrl != null && fileUrl.startsWith("/uploads/")) {
                try {
                    Path baseDir = Paths.get(uploadDir).toAbsolutePath().normalize();
                    Path filePath = baseDir.resolve(fileUrl.substring("/uploads/".length())).normalize();
                    if (!filePath.startsWith(baseDir)) {
                        throw new IllegalArgumentException("非法文件路径");
                    }
                    File f = filePath.toFile();
                    if (f.exists() && f.isFile()) {
                        boolean deleted = f.delete();
                        log.info("删除知识库磁盘文件: path={}, success={}", f.getAbsolutePath(), deleted);
                    }
                } catch (Exception e) {
                    log.warn("删除知识库磁盘文件失败: fileUrl={}", fileUrl, e);
                }
            }

            // 3. 删除知识库文档记录
            knowledgeDocumentMapper.deleteById(id);

            // 4. 重算所属知识库文档计数，避免 document_count 失真
            try {
                KnowledgeBase kb = knowledgeBaseMapper.selectById(doc.getKnowledgeBaseId());
                if (kb != null) {
                    kb.setDocumentCount(knowledgeDocumentMapper.findByKnowledgeBaseId(kb.getId()).size());
                    knowledgeBaseMapper.updateById(kb);
                }
            } catch (Exception e) {
                log.warn("重算知识库文档计数失败: kbId={}", doc.getKnowledgeBaseId(), e);
            }

            log.info("知识库文档删除成功: id={}", id);
            return Result.success(true);
        } catch (Exception e) {
            log.error("删除知识库文档失败", e);
            return Result.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 更新知识库文档
     */
    @PutMapping("/knowledge/document/{id}")
    public Result<Boolean> updateDocument(
            @RequestAttribute Long userId,
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        try {
            KnowledgeDocument doc = knowledgeDocumentMapper.selectById(id);
            if (doc == null) {
                return Result.error("文档不存在");
            }
            if (!hasDocPermission(doc, userId)) {
                return Result.error("无权修改他人文档");
            }
            if (body.containsKey("title")) {
                doc.setTitle(body.get("title"));
            }
            if (body.containsKey("summary")) {
                doc.setSummary(body.get("summary"));
            }
            if (body.containsKey("content")) {
                doc.setContent(body.get("content"));
            }
            // 待索引/索引失败的文档在更新后重新触发索引（与导入时的模拟索引策略一致）
            if (doc.getIndexStatus() == null || doc.getIndexStatus() != 2) {
                doc.setIndexStatus(2);
                doc.setChunkCount(doc.getContent() != null
                        ? (int) Math.ceil(doc.getContent().length() / 500.0) : 0);
                log.info("知识库文档重新索引完成: id={}", id);
            }
            knowledgeDocumentMapper.updateById(doc);
            log.info("知识库文档更新成功: id={}", id);
            return Result.success(true);
        } catch (Exception e) {
            log.error("更新知识库文档失败", e);
            return Result.error("更新失败: " + e.getMessage());
        }
    }
}
