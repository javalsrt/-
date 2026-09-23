package com.coursenote.service.impl;

import com.coursenote.common.BusinessException;
import com.coursenote.dto.AISummarizeRequest;
import com.coursenote.entity.Course;
import com.coursenote.entity.CourseSession;
import com.coursenote.entity.Material;
import com.coursenote.mapper.CourseMapper;
import com.coursenote.mapper.CourseSessionMapper;
import com.coursenote.mapper.MaterialMapper;
import com.coursenote.service.AIService;
import com.coursenote.service.SessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SessionServiceImpl implements SessionService {

    @Autowired
    private CourseSessionMapper sessionMapper;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private MaterialMapper materialMapper;

    @Autowired
    private AIService aiService;

    @Override
    public List<CourseSession> getSessionsByDate(Long userId, LocalDate date) {
        return sessionMapper.findByDate(userId, date);
    }

    @Override
    public List<CourseSession> getSessionsByCourse(Long userId, Long courseId) {
        // 验证课程归属
        Course course = courseMapper.selectById(courseId);
        if (course == null || !course.getUserId().equals(userId)) {
            throw new BusinessException("课程不存在");
        }
        return sessionMapper.findByCourseId(courseId);
    }

    @Override
    @Transactional
    public CourseSession startSession(Long userId, Long courseId) {
        Course course = courseMapper.selectById(courseId);
        if (course == null || !course.getUserId().equals(userId)) {
            throw new BusinessException("课程不存在");
        }

        // 检查今天是否已有进行的课段
        CourseSession ongoing = sessionMapper.findOngoingSession(userId, LocalDate.now());
        if (ongoing != null) {
            throw new BusinessException("已有进行中的课段，请先结束当前课段");
        }

        CourseSession session = new CourseSession();
        session.setCourseId(courseId);
        session.setUserId(userId);
        session.setSessionDate(LocalDate.now());
        session.setActualStartTime(java.time.LocalDateTime.now());
        session.setStatus(1); // 进行中

        sessionMapper.insert(session);
        log.info("开始课段: courseId={}, sessionId={}", courseId, session.getId());
        return session;
    }

    @Override
    @Transactional
    public CourseSession endSession(Long userId, Long sessionId) {
        CourseSession session = sessionMapper.findById(sessionId);
        if (session == null) {
            throw new BusinessException("课段不存在");
        }
        if (session.getStatus() != 1) {
            throw new BusinessException("课段未在进行中");
        }

        session.setActualEndTime(java.time.LocalDateTime.now());
        session.setStatus(2); // 已结束

        // 更新资料数量
        Integer count = materialMapper.countBySessionId(sessionId);
        session.setMaterialCount(count != null ? count : 0);

        sessionMapper.updateById(session);
        log.info("结束课段: sessionId={}", sessionId);

        // 异步触发AI整理归纳
        try {
            AISummarizeRequest req = new AISummarizeRequest();
            req.setSessionId(sessionId);
            req.setCourseId(session.getCourseId());
            aiService.summarizeSessionMaterials(req);
        } catch (Exception e) {
            log.error("AI整理归纳异步触发失败", e);
        }

        return session;
    }

    @Override
    public Map<String, Object> getSessionDetail(Long userId, Long sessionId) {
        CourseSession session = sessionMapper.findById(sessionId);
        if (session == null) {
            throw new BusinessException("课段不存在");
        }

        Course course = courseMapper.selectById(session.getCourseId());

        List<Material> materials = materialMapper.findBySessionId(sessionId);

        Map<String, List<Material>> grouped = materials.stream()
                .collect(Collectors.groupingBy(Material::getType, LinkedHashMap::new, Collectors.toList()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("session", session);
        result.put("course", course);
        result.put("photos", grouped.getOrDefault("PHOTO", Collections.emptyList()));
        result.put("notes", grouped.getOrDefault("NOTE", Collections.emptyList()));
        result.put("thirdParty", grouped.getOrDefault("THIRD_PARTY", Collections.emptyList()));
        result.put("totalCount", materials.size());

        return result;
    }

    @Override
    public CourseSession getCurrentSession(Long userId) {
        return sessionMapper.findOngoingSession(userId, LocalDate.now());
    }

    @Override
    public String summarizeSession(Long userId, Long sessionId) {
        CourseSession session = sessionMapper.findById(sessionId);
        if (session == null) {
            throw new BusinessException("课段不存在");
        }

        AISummarizeRequest request = new AISummarizeRequest();
        request.setSessionId(sessionId);
        request.setCourseId(session.getCourseId());

        String summary = aiService.summarizeSessionMaterials(request);

        // 保存总结
        session.setSummary(summary);
        session.setSummaryStatus(2);
        sessionMapper.updateById(session);

        return summary;
    }
}
