package com.coursenote.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_document")
public class KnowledgeDocument {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long knowledgeBaseId;
    private Long materialId;
    private Long userId;
    private String title;
    private String content;
    private String summary;
    private String fileUrl;
    private String vectorEmbedding;
    private Integer indexStatus = 0;
    private Integer chunkCount = 0;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
