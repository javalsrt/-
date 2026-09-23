package com.coursenote.controller;

import com.coursenote.common.Result;
import com.coursenote.dto.CourseDTO;
import com.coursenote.entity.Course;
import com.coursenote.service.CourseService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/courses")
public class CourseController {

    @Autowired
    private CourseService courseService;

    /**
     * 获取课表（按星期分组）
     */
    @GetMapping("/schedule")
    public Result<Map<Integer, List<Course>>> getSchedule(
            @RequestAttribute Long userId,
            @RequestParam(required = false) String semester) {
        return Result.success(courseService.getCourseSchedule(userId, semester));
    }

    /**
     * 获取课程详情
     */
    @GetMapping("/{id}")
    public Result<Course> getCourse(@RequestAttribute Long userId, @PathVariable Long id) {
        return Result.success(courseService.getById(id, userId));
    }

    /**
     * 创建课程
     */
    @PostMapping
    public Result<Course> createCourse(
            @RequestAttribute Long userId,
            @Valid @RequestBody CourseDTO courseDTO) {
        return Result.success("课程创建成功", courseService.createCourse(userId, courseDTO));
    }

    /**
     * 更新课程
     */
    @PutMapping("/{id}")
    public Result<Course> updateCourse(
            @RequestAttribute Long userId,
            @PathVariable Long id,
            @RequestBody CourseDTO courseDTO) {
        return Result.success("课程更新成功", courseService.updateCourse(id, userId, courseDTO));
    }

    /**
     * 删除课程
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteCourse(@RequestAttribute Long userId, @PathVariable Long id) {
        courseService.deleteCourse(id, userId);
        return Result.success("课程删除成功", null);
    }
}
