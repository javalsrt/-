package com.coursenote.controller;

import com.coursenote.common.Result;
import com.coursenote.entity.CourseSession;
import com.coursenote.service.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/sessions")
public class SessionController {

    @Autowired
    private SessionService sessionService;

    /**
     * 获取某天的课段列表
     */
    @GetMapping("/date")
    public Result<List<CourseSession>> getSessionsByDate(
            @RequestAttribute Long userId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        return Result.success(sessionService.getSessionsByDate(userId, date));
    }

    /**
     * 获取某门课程的所有课段
     */
    @GetMapping("/course/{courseId}")
    public Result<List<CourseSession>> getSessionsByCourse(
            @RequestAttribute Long userId,
            @PathVariable Long courseId) {
        return Result.success(sessionService.getSessionsByCourse(userId, courseId));
    }

    /**
     * 获取课段详情（含所有资料）
     */
    @GetMapping("/{sessionId}/detail")
    public Result<Map<String, Object>> getSessionDetail(
            @RequestAttribute Long userId,
            @PathVariable Long sessionId) {
        return Result.success(sessionService.getSessionDetail(userId, sessionId));
    }

    /**
     * 获取当前进行的课段
     */
    @GetMapping("/current")
    public Result<CourseSession> getCurrentSession(@RequestAttribute Long userId) {
        return Result.success(sessionService.getCurrentSession(userId));
    }

    /**
     * 开始课段（签到上课）
     */
    @PostMapping("/start/{courseId}")
    public Result<CourseSession> startSession(
            @RequestAttribute Long userId,
            @PathVariable Long courseId) {
        return Result.success("开始上课", sessionService.startSession(userId, courseId));
    }

    /**
     * 结束课段（下课）
     */
    @PostMapping("/end/{sessionId}")
    public Result<CourseSession> endSession(
            @RequestAttribute Long userId,
            @PathVariable Long sessionId) {
        return Result.success("下课", sessionService.endSession(userId, sessionId));
    }

    /**
     * AI整理归纳课段内容
     */
    @PostMapping("/{sessionId}/summarize")
    public Result<String> summarizeSession(
            @RequestAttribute Long userId,
            @PathVariable Long sessionId) {
        String summary = sessionService.summarizeSession(userId, sessionId);
        return Result.success("课段总结生成完毕", summary);
    }
}
