package com.coursenote.service.impl;

import com.coursenote.common.BusinessException;
import com.coursenote.entity.Material;
import com.coursenote.mapper.MaterialMapper;
import com.coursenote.service.AIService;
import com.coursenote.service.KnowledgeBaseService;
import com.coursenote.service.MaterialService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class MaterialServiceImpl implements MaterialService {

    @Autowired
    private MaterialMapper materialMapper;

    @Autowired
    private AIService aiService;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Override
    public List<Material> getSessionMaterials(Long sessionId) {
        return materialMapper.findBySessionId(sessionId);
    }

    @Override
    @Transactional
    public Material uploadPhoto(Long userId, Long sessionId, Long courseId,
                                MultipartFile file) {
        String fileUrl = saveFile(file, "photo");

        Material material = new Material();
        material.setSessionId(sessionId);
        material.setCourseId(courseId);
        material.setUserId(userId);
        material.setType("PHOTO");
        material.setTitle(file.getOriginalFilename());
        material.setFileUrl(fileUrl);
        material.setFileSize(file.getSize());
        material.setFormat(getFileExtension(file.getOriginalFilename()));

        materialMapper.insert(material);

        try {
            // AI标签生成
            String tags = aiService.generateTags(material.getTitle(), "PHOTO");
            material.setAiTags(tags);
            material.setAiProcessed(1);
            materialMapper.updateById(material);

            knowledgeBaseService.importDocument(courseId, material.getId(), userId);
        } catch (Exception e) {
            log.error("AI处理照片失败: materialId={}", material.getId(), e);
        }

        return material;
    }

    @Override
    public Material createNote(Long userId, Long sessionId, Long courseId,
                               String title, String content) {
        Material material = new Material();
        material.setSessionId(sessionId);
        material.setCourseId(courseId);
        material.setUserId(userId);
        material.setType("NOTE");
        material.setTitle(title != null ? title : "课堂笔记");
        material.setContent(content);

        materialMapper.insert(material);

        // 异步生成AI标签并导入知识库
        try {
            String tags = aiService.generateTags(content, "NOTE");
            material.setAiTags(tags);
            material.setAiProcessed(1);
            materialMapper.updateById(material);

            knowledgeBaseService.importDocument(courseId, material.getId(), userId);
        } catch (Exception e) {
            log.error("AI处理笔记失败", e);
        }

        return material;
    }

    @Override
    public Material importMaterial(Long userId, Long sessionId, Long courseId,
                                   String title, String description, String fileUrl, String source) {
        Material material = new Material();
        material.setSessionId(sessionId);
        material.setCourseId(courseId);
        material.setUserId(userId);
        material.setType("THIRD_PARTY");
        material.setTitle(title);
        material.setDescription(description);
        material.setFileUrl(fileUrl);
        material.setSource(source);

        materialMapper.insert(material);

        return material;
    }

    @Override
    public void deleteMaterial(Long userId, Long materialId) {
        Material material = materialMapper.findById(materialId);
        if (material == null || !material.getUserId().equals(userId)) {
            throw new BusinessException("资料不存在");
        }
        materialMapper.deleteById(materialId);
    }

    @Override
    public Material getById(Long id) {
        Material material = materialMapper.findById(id);
        if (material == null) {
            throw new BusinessException("资料不存在");
        }
        return material;
    }

    @Override
    public Material processWithAI(Long materialId) {
        Material material = getById(materialId);

        String tags = aiService.generateTags(
                material.getContent() != null ? material.getContent() : material.getTitle(),
                material.getType()
        );
        material.setAiTags(tags);
        material.setAiProcessed(1);
        materialMapper.updateById(material);

        return material;
    }

    // ===== 文件存储辅助方法 =====

    private String saveFile(MultipartFile file, String prefix) {
        try {
            String originalName = file.getOriginalFilename();
            String ext = getFileExtension(originalName);
            String newName = prefix + "/" + UUID.randomUUID() + "." + ext;

            Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path uploadPath = root.resolve(newName).normalize();
            if (!uploadPath.startsWith(root)) {
                throw new BusinessException("非法文件路径");
            }
            Files.createDirectories(uploadPath.getParent());
            file.transferTo(uploadPath.toFile());

            return "/uploads/" + newName;
        } catch (IOException e) {
            log.error("文件上传失败", e);
            throw new BusinessException("文件上传失败: " + e.getMessage());
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "unknown";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }
}
