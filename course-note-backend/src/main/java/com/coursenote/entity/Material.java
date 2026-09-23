package com.coursenote.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("material")
public class Material {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private Long courseId;
    private Long userId;
    private String type;
    private String title;
    private String description;
    private String content;
    private String fileUrl;
    private Long fileSize = 0L;
    private String format;
    private String aiTags;
    private String aiSummary;
    private Integer aiProcessed = 0;
    private String source = "self";
    private Integer sortOrder = 0;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
