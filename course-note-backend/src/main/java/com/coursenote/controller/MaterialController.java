package com.coursenote.controller;

import com.coursenote.common.Result;
import com.coursenote.entity.Material;
import com.coursenote.service.MaterialService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/materials")
public class MaterialController {

    @Autowired
    private MaterialService materialService;

    /**
     * 获取课段所有资料
     */
    @GetMapping("/session/{sessionId}")
    public Result<List<Material>> getSessionMaterials(@PathVariable Long sessionId) {
        return Result.success(materialService.getSessionMaterials(sessionId));
    }

    /**
     * 上传照片
     */
    @PostMapping("/photo")
    public Result<Material> uploadPhoto(
            @RequestAttribute Long userId,
            @RequestParam Long sessionId,
            @RequestParam Long courseId,
            @RequestParam("file") MultipartFile file) {
        Material material = materialService.uploadPhoto(
                userId, sessionId, courseId, file);
        return Result.success("照片上传成功", material);
    }

    /**
     * 创建文字笔记
     */
    @PostMapping("/note")
    public Result<Material> createNote(
            @RequestAttribute Long userId,
            @RequestParam Long sessionId,
            @RequestParam Long courseId,
            @RequestParam(defaultValue = "课堂笔记") String title,
            @RequestBody String content) {
        Material material = materialService.createNote(
                userId, sessionId, courseId, title, content);
        return Result.success("笔记创建成功", material);
    }

    /**
     * 导入第三方资料
     */
    @PostMapping("/import")
    public Result<Material> importMaterial(
            @RequestAttribute Long userId,
            @RequestParam Long sessionId,
            @RequestParam Long courseId,
            @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam String fileUrl,
            @RequestParam(defaultValue = "external") String source) {
        Material material = materialService.importMaterial(
                userId, sessionId, courseId, title, description, fileUrl, source);
        return Result.success("导入成功", material);
    }

    /**
     * AI处理资料（OCR/标签生成）
     */
    @PostMapping("/{materialId}/ai-process")
    public Result<Material> processWithAI(@PathVariable Long materialId) {
        return Result.success(materialService.processWithAI(materialId));
    }

    /**
     * 删除资料
     */
    @DeleteMapping("/{materialId}")
    public Result<Void> deleteMaterial(
            @RequestAttribute Long userId,
            @PathVariable Long materialId) {
        materialService.deleteMaterial(userId, materialId);
        return Result.success("删除成功", null);
    }
}
