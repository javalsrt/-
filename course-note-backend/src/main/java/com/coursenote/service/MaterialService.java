package com.coursenote.service;

import com.coursenote.entity.Material;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface MaterialService {
    /**
     * 获取课段的所有资料
     */
    List<Material> getSessionMaterials(Long sessionId);

    /**
     * 上传照片/图片
     */
    Material uploadPhoto(Long userId, Long sessionId, Long courseId,
                         MultipartFile file);

    /**
     * 创建文本笔记
     */
    Material createNote(Long userId, Long sessionId, Long courseId,
                        String title, String content);

    /**
     * 导入第三方材料
     */
    Material importMaterial(Long userId, Long sessionId, Long courseId,
                            String title, String description, String fileUrl, String source);

    /**
     * 删除资料
     */
    void deleteMaterial(Long userId, Long materialId);

    /**
     * 获取资料详情
     */
    Material getById(Long id);

    /**
     * 执行AI增强处理（OCR/标签/摘要）
     */
    Material processWithAI(Long materialId);
}
