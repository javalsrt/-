package com.znxsgl.controller;

import com.znxsgl.dto.TeachingTaskSaveRequest;
import com.znxsgl.entity.*;
import com.znxsgl.mapper.*;
import com.znxsgl.service.AutoScheduleService;
import com.znxsgl.service.SemesterService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 管理端 - 教务排课资源管理接口
 *
 * 包含：
 *   1. 教室资源管理（CRUD）
 *   2. 教学任务管理（批量导入、列表、删除）
 *   3. 排课结果统计 + 失败报告
 *
 * 教师与管理员均可访问（教师承担本班排课职责）。
 *
 * ⚠️ 适配：本项目 context-path=/api，controller 映射基于应用内路径（不含 /api 前缀），
 *    原 "/api/admin/schedule" 改为 "/admin/schedule"，对外实际 URL 仍是 /api/admin/schedule/...。
 */
@RestController
@RequestMapping("/admin/schedule")
@PreAuthorize("hasRole('ADMIN') or hasRole('TEACHER')")
public class AdminScheduleController {

    private final ClassroomMapper classroomMapper;
    private final TeachingTaskMapper teachingTaskMapper;
    private final CourseMapper courseMapper;
    private final TeacherMapper teacherMapper;
    private final ClassInfoMapper classInfoMapper;
    private final UserMapper userMapper;
    private final AutoScheduleService autoScheduleService;
    private final SemesterService semesterService;

    public AdminScheduleController(ClassroomMapper classroomMapper,
                                    TeachingTaskMapper teachingTaskMapper,
                                    CourseMapper courseMapper,
                                    TeacherMapper teacherMapper,
                                    ClassInfoMapper classInfoMapper,
                                    UserMapper userMapper,
                                    AutoScheduleService autoScheduleService,
                                    SemesterService semesterService) {
        this.classroomMapper = classroomMapper;
        this.teachingTaskMapper = teachingTaskMapper;
        this.courseMapper = courseMapper;
        this.teacherMapper = teacherMapper;
        this.classInfoMapper = classInfoMapper;
        this.userMapper = userMapper;
        this.autoScheduleService = autoScheduleService;
        this.semesterService = semesterService;
    }

    /** 判断当前用户是否为管理员 */
    private boolean isAdmin(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    /**
     * 获取当前登录教师对应的 teacher.id。
     * 非教师角色返回 null；教师但 teacher 表无记录时返回 null（此时看不到任务）。
     */
    private Long getCurrentTeacherId(Authentication auth) {
        if (auth == null) return null;
        Long userId = (Long) auth.getPrincipal();
        User user = userMapper.selectById(userId);
        if (user == null || user.getRole() == null || user.getRole() != 2) return null;
        Teacher teacher = teacherMapper.selectOne(
                new LambdaQueryWrapper<Teacher>().eq(Teacher::getRealName, user.getRealName()));
        return teacher != null ? teacher.getId() : null;
    }

    // ============================================================
    //  教室资源管理
    // ============================================================

    /** 教室列表 */
    @GetMapping("/classrooms")
    public ResponseEntity<List<Classroom>> listClassrooms(
            @RequestParam(required = false) String type) {
        LambdaQueryWrapper<Classroom> qw = new LambdaQueryWrapper<Classroom>()
                .eq(Classroom::getIsActive, 1)
                .orderByAsc(Classroom::getBuilding)
                .orderByAsc(Classroom::getFloor)
                .orderByAsc(Classroom::getName);
        if (type != null && !type.isEmpty()) {
            qw.eq(Classroom::getType, type);
        }
        return ResponseEntity.ok(classroomMapper.selectList(qw));
    }

    /** 新增教室 */
    @PostMapping("/classroom")
    public ResponseEntity<Map<String, Object>> addClassroom(@RequestBody Classroom classroom) {
        classroom.setIsActive(1);
        classroomMapper.insert(classroom);
        return ResponseEntity.ok(Map.of("id", classroom.getId(), "message", "教室添加成功"));
    }

    /** 修改教室 */
    @PutMapping("/classroom/{id}")
    public ResponseEntity<Map<String, String>> updateClassroom(@PathVariable Long id,
                                                                @RequestBody Classroom classroom) {
        classroom.setId(id);
        classroomMapper.updateById(classroom);
        return ResponseEntity.ok(Map.of("message", "教室更新成功"));
    }

    /** 删除教室（软删除：is_active=0） */
    @DeleteMapping("/classroom/{id}")
    public ResponseEntity<Map<String, String>> deleteClassroom(@PathVariable Long id) {
        classroomMapper.update(null,
                new LambdaUpdateWrapper<Classroom>()
                        .eq(Classroom::getId, id)
                        .set(Classroom::getIsActive, 0));
        return ResponseEntity.ok(Map.of("message", "教室已删除"));
    }

    // ============================================================
    //  教学任务管理
    // ============================================================

    /** 教学任务列表（管理员看全部，教师只看自己的） */
    @GetMapping("/tasks")
    public ResponseEntity<List<TeachingTask>> listTasks(
            Authentication auth,
            @RequestParam(required = false) String semester,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long teacherId) {
        String sem = semester != null ? semester : semesterService.getCurrentSemesterName();
        if (sem == null) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        // 权限：教师只能查看自己的任务；管理员可通过 teacherId 筛选（未传则看全部）
        Long effectiveTeacherId = null;
        if (!isAdmin(auth)) {
            effectiveTeacherId = getCurrentTeacherId(auth);
            if (effectiveTeacherId == null) {
                return ResponseEntity.ok(Collections.emptyList());
            }
        } else if (teacherId != null) {
            effectiveTeacherId = teacherId;
        }

        LambdaQueryWrapper<TeachingTask> qw = new LambdaQueryWrapper<TeachingTask>()
                .eq(TeachingTask::getSemester, sem)
                .orderByAsc(TeachingTask::getClassId)
                .orderByAsc(TeachingTask::getPriority);
        if (classId != null) qw.eq(TeachingTask::getClassId, classId);
        if (status != null) qw.eq(TeachingTask::getStatus, status);
        if (effectiveTeacherId != null) qw.eq(TeachingTask::getTeacherId, effectiveTeacherId);

        return ResponseEntity.ok(teachingTaskMapper.selectList(qw));
    }

    /** 批量导入教学任务 */
    @PostMapping("/tasks/batch")
    public ResponseEntity<Map<String, Object>> batchImportTasks(@RequestBody List<TeachingTaskSaveRequest> tasks) {
        String semester = semesterService.getCurrentSemesterName();
        if (semester == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "当前学期未配置"));
        }

        int created = 0, updated = 0;
        List<String> errors = new ArrayList<>();

        for (TeachingTaskSaveRequest req : tasks) {
            try {
                // 校验班级存在
                if (classInfoMapper.selectById(req.getClassId()) == null) {
                    errors.add("班级不存在：id=" + req.getClassId());
                    continue;
                }
                // 校验课程存在
                Course course = courseMapper.selectById(req.getCourseId());
                if (course == null) {
                    errors.add("课程不存在：id=" + req.getCourseId());
                    continue;
                }
                // 校验教师存在
                Teacher teacher = null;
                if (req.getTeacherId() != null) {
                    teacher = teacherMapper.selectById(req.getTeacherId());
                    if (teacher == null) {
                        errors.add("教师不存在：id=" + req.getTeacherId());
                        continue;
                    }
                }

                // 查找是否已存在（同一学期同一班级同一课程）
                TeachingTask existing = teachingTaskMapper.selectOne(
                        new LambdaQueryWrapper<TeachingTask>()
                                .eq(TeachingTask::getSemester, semester)
                                .eq(TeachingTask::getClassId, req.getClassId())
                                .eq(TeachingTask::getCourseId, req.getCourseId()));

                TeachingTask task = new TeachingTask();
                task.setSemester(semester);
                task.setClassId(req.getClassId());
                task.setCourseId(req.getCourseId());
                task.setCourseName(course.getCourseName());
                task.setTeacherId(req.getTeacherId());
                task.setTeacherName(teacher != null ? teacher.getRealName() : null);
                task.setWeeklyHours(req.getWeeklyHours() != null ? req.getWeeklyHours() : 2);
                task.setConsecutive(req.getConsecutive() != null ? req.getConsecutive() : 1);
                task.setPreferredRoomType(req.getPreferredRoomType());
                task.setPreferredPeriod(req.getPreferredPeriod() != null ? req.getPreferredPeriod() : "any");
                task.setPriority(req.getPriority() != null ? req.getPriority() : 5);

                if (existing != null) {
                    task.setId(existing.getId());
                    task.setStatus(existing.getStatus());
                    teachingTaskMapper.updateById(task);
                    updated++;
                } else {
                    task.setStatus("pending");
                    teachingTaskMapper.insert(task);
                    created++;
                }
            } catch (Exception e) {
                errors.add("课程" + req.getCourseId() + "导入失败：" + e.getMessage());
            }
        }

        return ResponseEntity.ok(Map.of(
                "created", created,
                "updated", updated,
                "errors", errors,
                "message", String.format("导入完成：新增 %d，更新 %d，失败 %d",
                        created, updated, errors.size())
        ));
    }

    /** 删除单个教学任务 */
    @DeleteMapping("/task/{id}")
    public ResponseEntity<Map<String, String>> deleteTask(@PathVariable Long id) {
        teachingTaskMapper.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "任务已删除"));
    }

    /** 清空教学任务：管理员清空当前学期全部，教师只清空自己的任务 */
    @DeleteMapping("/tasks/clear")
    public ResponseEntity<Map<String, String>> clearTasks(
            Authentication auth,
            @RequestParam(required = false) String semester) {
        String sem = semester != null ? semester : semesterService.getCurrentSemesterName();
        if (sem == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "当前学期未配置"));
        }
        LambdaQueryWrapper<TeachingTask> qw = new LambdaQueryWrapper<TeachingTask>()
                .eq(TeachingTask::getSemester, sem);
        if (!isAdmin(auth)) {
            Long teacherId = getCurrentTeacherId(auth);
            if (teacherId == null) {
                return ResponseEntity.ok(Map.of("message", "已删除 0 条教学任务"));
            }
            qw.eq(TeachingTask::getTeacherId, teacherId);
        }
        int count = teachingTaskMapper.delete(qw);
        return ResponseEntity.ok(Map.of("message", "已删除 " + count + " 条教学任务"));
    }

    // ============================================================
    //  一键自动排课
    // ============================================================

    /**
     * 执行自动排课。
     *
     * @param clearExisting true=清空现有非锁定课表全量重排，false=只排未排的任务
     * @param teacherId 管理员可指定只排某位教师的任务；教师不传则自动只排自己的任务
     */
    @PostMapping("/auto-generate")
    public ResponseEntity<AutoScheduleService.ScheduleResult> autoGenerate(
            Authentication auth,
            @RequestParam(required = false) String semester,
            @RequestParam(defaultValue = "true") boolean clearExisting,
            @RequestParam(required = false) Long teacherId) {
        String sem = semester != null ? semester : semesterService.getCurrentSemesterName();
        if (sem == null) {
            AutoScheduleService.ScheduleResult r = new AutoScheduleService.ScheduleResult();
            r.message = "当前学期未配置，请先设置当前学期";
            return ResponseEntity.badRequest().body(r);
        }

        Long effectiveTeacherId = null;
        if (!isAdmin(auth)) {
            effectiveTeacherId = getCurrentTeacherId(auth);
            if (effectiveTeacherId == null) {
                AutoScheduleService.ScheduleResult r = new AutoScheduleService.ScheduleResult();
                r.message = "未找到当前教师信息";
                return ResponseEntity.badRequest().body(r);
            }
        } else if (teacherId != null) {
            effectiveTeacherId = teacherId;
        }

        AutoScheduleService.ScheduleResult result = autoScheduleService.autoSchedule(sem, clearExisting, effectiveTeacherId);
        return ResponseEntity.ok(result);
    }

    /**
     * 排课统计（按状态分类）。管理员统计全部，教师只统计自己的任务。
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats(
            Authentication auth,
            @RequestParam(required = false) String semester,
            @RequestParam(required = false) Long teacherId) {
        String sem = semester != null ? semester : semesterService.getCurrentSemesterName();
        if (sem == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "当前学期未配置"));
        }

        // 权限：教师只能统计自己的任务；管理员可通过 teacherId 筛选
        Long effectiveTeacherId = null;
        if (!isAdmin(auth)) {
            effectiveTeacherId = getCurrentTeacherId(auth);
            if (effectiveTeacherId == null) {
                Map<String, Object> empty = new LinkedHashMap<>();
                empty.put("semester", sem);
                empty.put("total", 0); empty.put("scheduled", 0); empty.put("failed", 0);
                empty.put("pending", 0); empty.put("locked", 0);
                return ResponseEntity.ok(empty);
            }
        } else if (teacherId != null) {
            effectiveTeacherId = teacherId;
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("semester", sem);
        stats.put("total", countByStatus(sem, effectiveTeacherId, null));
        stats.put("scheduled", countByStatus(sem, effectiveTeacherId, "scheduled"));
        stats.put("failed", countByStatus(sem, effectiveTeacherId, "failed"));
        stats.put("pending", countByStatus(sem, effectiveTeacherId, "pending"));
        stats.put("locked", countByStatus(sem, effectiveTeacherId, "locked"));

        return ResponseEntity.ok(stats);
    }

    private long countByStatus(String semester, Long teacherId, String status) {
        LambdaQueryWrapper<TeachingTask> qw = new LambdaQueryWrapper<TeachingTask>()
                .eq(TeachingTask::getSemester, semester);
        if (status != null) qw.eq(TeachingTask::getStatus, status);
        if (teacherId != null) qw.eq(TeachingTask::getTeacherId, teacherId);
        return teachingTaskMapper.selectCount(qw);
    }

    /**
     * 查询排课失败的任务列表
     */
    @GetMapping("/failures")
    public ResponseEntity<List<TeachingTask>> listFailures(
            @RequestParam(required = false) String semester) {
        String sem = semester != null ? semester : semesterService.getCurrentSemesterName();
        if (sem == null) return ResponseEntity.ok(Collections.emptyList());

        return ResponseEntity.ok(teachingTaskMapper.selectList(
                new LambdaQueryWrapper<TeachingTask>()
                        .eq(TeachingTask::getSemester, sem)
                        .eq(TeachingTask::getStatus, "failed")
                        .orderByAsc(TeachingTask::getClassId)));
    }
}