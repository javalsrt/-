package com.coursenote.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
public class CourseDTO {
    @NotBlank(message = "课程名称不能为空")
    private String name;
    private String teacher;
    private String classroom;

    @NotNull(message = "星期不能为空")
    private Integer dayOfWeek;

    @NotBlank(message = "开始时间不能为空")
    private String startTime;

    @NotBlank(message = "结束时间不能为空")
    private String endTime;

    private String weeks;
    private String color;
    private String semester;
    private String description;
}
