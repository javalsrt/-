package com.coursenote.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_chat_record")
public class AiChatRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long courseId;
    private Long knowledgeBaseId;
    private Long sessionId;
    private String role;
    private String content;
    private Integer tokens = 0;
    private String model = "deepseek-chat";

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
