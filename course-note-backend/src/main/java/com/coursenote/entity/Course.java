package com.coursenote.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 个人课表条目（原 course 表，因与教务课程表 course 重名而改名 personal_course）
 */
@Data
@TableName("personal_course")
public class Course {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String name;
    private String teacher;
    private String classroom;
    private Integer dayOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;
    private String weeks;
    private String color;
    private String semester;
    private String description;
    private Integer status = 1;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
