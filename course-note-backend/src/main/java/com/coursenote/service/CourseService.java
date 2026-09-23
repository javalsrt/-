package com.coursenote.service;

import com.coursenote.entity.Course;
import com.coursenote.dto.CourseDTO;

import java.util.List;
import java.util.Map;

public interface CourseService {
    /**
     * 获取用户的课程列表（按课表格式返回）
     */
    Map<Integer, List<Course>> getCourseSchedule(Long userId, String semester);

    /**
     * 获取课程详情
     */
    Course getById(Long id, Long userId);

    /**
     * 创建课程
     */
    Course createCourse(Long userId, CourseDTO courseDTO);

    /**
     * 更新课程
     */
    Course updateCourse(Long id, Long userId, CourseDTO courseDTO);

    /**
     * 删除课程
     */
    void deleteCourse(Long id, Long userId);
}
