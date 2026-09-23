package com.coursenote.controller;

import com.coursenote.common.Result;
import com.coursenote.entity.Course;
import com.coursenote.entity.Material;
import com.coursenote.entity.User;
import com.coursenote.mapper.CourseMapper;
import com.coursenote.mapper.MaterialMapper;
import com.coursenote.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/files")
public class FileController {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "pdf", "txt", "md", "doc", "docx", "ppt", "pptx");

    private final MaterialMapper materialMapper;
    private final UserMapper userMapper;
    private final CourseMapper courseMapper;
    private final Path uploadRoot;

    public FileController(MaterialMapper materialMapper,
                          UserMapper userMapper,
                          CourseMapper courseMapper,
                          @Value("${file.upload-dir}") String uploadDir) {
        this.materialMapper = materialMapper;
        this.userMapper = userMapper;
        this.courseMapper = courseMapper;
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @PostMapping("/upload")
    public Result<Map<String, Object>> uploadFile(@RequestAttribute Long userId,
                                                   @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.error(400, "请选择要上传的文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            return Result.error(400, "文件大小不能超过10MB");
        }

        String originalName = file.getOriginalFilename();
        String extension = getExtension(originalName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            return Result.error(400, "不支持该文件类型");
        }

        try {
            Path directory = uploadRoot.resolve("common").resolve(String.valueOf(userId));
            Files.createDirectories(directory);
            String storedName = UUID.randomUUID() + "." + extension;
            Path target = directory.resolve(storedName).normalize();
            if (!target.startsWith(uploadRoot)) {
                return Result.error(400, "非法文件路径");
            }
            file.transferTo(target);

            Material material = new Material();
            material.setSessionId(0L);
            material.setUserId(userId);
            material.setType("THIRD_PARTY");
            material.setTitle(originalName == null || originalName.isBlank() ? storedName : originalName);
            material.setFileUrl("/uploads/common/" + userId + "/" + storedName);
            material.setFileSize(file.getSize());
            material.setFormat(extension);
            material.setAiProcessed(0);
            materialMapper.insert(material);

            return Result.success("上传成功", Map.of(
                    "materialId", material.getId(),
                    "fileUrl", material.getFileUrl(),
                    "previewUrl", "/files/materials/" + material.getId() + "/preview"));
        } catch (IOException e) {
            return Result.error("文件上传失败");
        }
    }

    @GetMapping("/materials/{id}/preview")
    public ResponseEntity<Resource> previewMaterial(@RequestAttribute Long userId,
                                                     @PathVariable Long id) throws IOException {
        Material material = materialMapper.findById(id);
        if (material == null || material.getFileUrl() == null || material.getFileUrl().isBlank()) {
            return ResponseEntity.notFound().build();
        }
        User user = userMapper.selectById(userId);
        if (user == null || !canPreview(user, material)) {
            return ResponseEntity.status(403).build();
        }

        Path file = resolveStoredFile(material.getFileUrl());
        if (!Files.isRegularFile(file)) {
            return ResponseEntity.notFound().build();
        }

        String contentType = Files.probeContentType(file);
        MediaType mediaType;
        try {
            mediaType = contentType == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(contentType);
        } catch (IllegalArgumentException e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        String filename = material.getTitle() == null || material.getTitle().isBlank()
                ? file.getFileName().toString() : material.getTitle();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(Files.size(file))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(filename).build().toString())
                .body(new FileSystemResource(file));
    }

    private boolean canPreview(User user, Material material) {
        if (Integer.valueOf(3).equals(user.getRole()) || user.getId().equals(material.getUserId())) {
            return true;
        }
        if (material.getCourseId() == null) {
            return false;
        }
        Course course = courseMapper.selectById(material.getCourseId());
        return course != null && user.getId().equals(course.getUserId());
    }

    private Path resolveStoredFile(String fileUrl) {
        String prefix = "/uploads/";
        if (!fileUrl.startsWith(prefix)) {
            throw new IllegalArgumentException("非法文件地址");
        }
        Path file = uploadRoot.resolve(fileUrl.substring(prefix.length())).normalize();
        if (!file.startsWith(uploadRoot)) {
            throw new IllegalArgumentException("非法文件路径");
        }
        return file;
    }

    private String getExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int index = filename.lastIndexOf('.');
        return index < 1 || index == filename.length() - 1
                ? "" : filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }
}
