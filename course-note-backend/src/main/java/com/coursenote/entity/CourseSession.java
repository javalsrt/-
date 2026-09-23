package com.coursenote.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("course_session")
public class CourseSession {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long courseId;
    private Long userId;
    private LocalDate sessionDate;
    private LocalDateTime actualStartTime;
    private LocalDateTime actualEndTime;
    private Integer status = 0;
    private String summary;
    private Integer summaryStatus = 0;
    private Integer materialCount = 0;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
