package com.coursenote.service;

import com.coursenote.entity.CourseSession;
import com.coursenote.entity.Material;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface SessionService {
    /**
     * 获取某天的课段列表
     */
    List<CourseSession> getSessionsByDate(Long userId, LocalDate date);

    /**
     * 获取课程的课段列表
     */
    List<CourseSession> getSessionsByCourse(Long userId, Long courseId);

    /**
     * 开始一个课段（签到/上课）
     */
    CourseSession startSession(Long userId, Long courseId);

    /**
     * 结束一个课段（下课）
     */
    CourseSession endSession(Long userId, Long sessionId);

    /**
     * 获取课段详情（含所有资料）
     */
    Map<String, Object> getSessionDetail(Long userId, Long sessionId);

    /**
     * 获取当前正在进行的课段
     */
    CourseSession getCurrentSession(Long userId);

    /**
     * AI整理归纳课段内容
     */
    String summarizeSession(Long userId, Long sessionId);
}
