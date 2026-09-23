package com.znxsgl.controller;

import com.znxsgl.service.SemesterService;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 教师班级管理接口
 *
 * 权限：仅教师或管理员可访问。
 *
 * ⚠️ 适配：本项目 context-path=/api，controller 映射基于应用内路径（不含 /api 前缀），
 *    原 "/api/teacher/class" 改为 "/teacher/class"，对外实际 URL 仍是 /api/teacher/class/...。
 */
@RestController
@RequestMapping("/teacher/class")
@PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
public class TeacherClassController {

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final SemesterService semesterService;

    public TeacherClassController(JdbcTemplate jdbc, PasswordEncoder passwordEncoder,
                                   SemesterService semesterService) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.semesterService = semesterService;
    }

    /** 列出教师管理的所有班级 */
    @GetMapping("/list")
    public ResponseEntity<List<Map<String, Object>>> listClasses(Authentication auth) {
        Long teacherUserId = (Long) auth.getPrincipal();
        Long realTeacherId = getRealTeacherId(teacherUserId);
        List<Map<String, Object>> list = jdbc.queryForList(
            "SELECT DISTINCT ci.id, ci.class_name AS className, ci.major, ci.grade, ci.department, " +
            "  (SELECT COUNT(*) FROM user u WHERE u.class_id = ci.id AND u.role = 1) AS studentCount " +
            "FROM class_info ci " +
            "JOIN course_class cc ON cc.class_id = ci.id " +
            "JOIN course c ON c.id = cc.course_id " +
            "WHERE c.teacher_id = ? ORDER BY ci.id", realTeacherId);
        return ResponseEntity.ok(list);
    }

    /** 创建班级（含课程+课表+关联） */
    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createClass(Authentication auth, @RequestBody Map<String, Object> body) {
        Long teacherUserId = (Long) auth.getPrincipal();
        String className = safeStr(body, "className");
        String major = safeStr(body, "major", "计算机科学与技术");
        String grade = safeStr(body, "grade", "2023");
        String department = safeStr(body, "department", "计算机与信息工程学院");
        String semester = safeStr(body, "semester", semesterService.getCurrentSemesterName());

        // 创建班级
        jdbc.update("INSERT INTO class_info (class_name, major, department, grade) VALUES (?,?,?,?)",
                className, major, department, grade);
        Long classId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("classId", classId);
        result.put("className", className);
        result.put("msg", "班级创建成功");
        return ResponseEntity.ok(result);
    }

    /**
     * 教师修改自己课程的每周课时上限（credit）。
     * 管理员可修改任意课程。
     */
    @PostMapping("/update-course-credit")
    public ResponseEntity<Map<String, Object>> updateCourseCredit(Authentication auth, @RequestBody Map<String, Object> body) {
        Long teacherUserId = (Long) auth.getPrincipal();
        Long courseId = Long.valueOf(body.get("courseId").toString());
        int credit;
        try {
            credit = Integer.parseInt(body.get("credit").toString());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "credit 必须是数字"));
        }

        // 范围校验：1~20 课时/周，避免极端值
        if (credit < 1 || credit > 20) {
            return ResponseEntity.badRequest().body(Map.of("error", "课时范围必须在 1~20 之间"));
        }

        // 权限校验
        boolean admin = isAdmin(auth);
        if (!admin) {
            Long realTeacherId = getRealTeacherId(teacherUserId);
            if (!courseBelongsToTeacher(courseId, realTeacherId)) {
                return ResponseEntity.status(403).body(Map.of("error", "无权限修改该课程"));
            }
        }

        // 更新 credit
        int rows = jdbc.update("UPDATE course SET credit = ? WHERE id = ?", credit, courseId);
        if (rows == 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "课程不存在"));
        }
        return ResponseEntity.ok(Map.of("msg", "课程课时上限已更新", "courseId", courseId, "credit", credit));
    }

    /** 批量导入学生 */
    @PostMapping("/import-students")
    public ResponseEntity<Map<String, Object>> importStudents(Authentication auth, @RequestBody Map<String, Object> body) {
        Long teacherUserId = (Long) auth.getPrincipal();
        Long classId = Long.valueOf(body.get("classId").toString());
        String studentText = body.get("students").toString();
        String defaultPassword = safeStr(body, "password", "123456");

        // 校验班级归属当前教师
        Long realTeacherId = getRealTeacherId(teacherUserId);
        if (!classBelongsToTeacher(classId, realTeacherId)) {
            return ResponseEntity.status(403).body(Map.of("error", "无权限"));
        }

        String encodedPwd = passwordEncoder.encode(defaultPassword);
        int created = 0;
        List<String> errors = new ArrayList<>();

        for (String line : studentText.split("\\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;

            // 支持格式：学号 \t 姓名  或  学号,姓名
            String[] parts = line.split("[\\t,]");
            if (parts.length < 2) {
                errors.add("格式错误: " + line);
                continue;
            }
            String studentNo = parts[0].trim();
            String realName = parts[1].trim();

            // 检查学号是否已存在
            int exist = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user WHERE student_no = ?", Integer.class, studentNo);
            if (exist > 0) {
                errors.add("学号已存在: " + studentNo);
                continue;
            }

            jdbc.update("INSERT INTO user (student_no, username, password_hash, real_name, role, class_id, major, grade, status) " +
                    "VALUES (?,?,?,?,1,?,(SELECT major FROM class_info WHERE id=?), (SELECT grade FROM class_info WHERE id=?), 1)",
                    studentNo, studentNo, encodedPwd, realName, classId, classId, classId);
            created++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("created", created);
        result.put("errors", errors);
        result.put("msg", String.format("成功添加 %d 名学生", created));
        return ResponseEntity.ok(result);
    }

    // ===== 辅助方法 =====

    private int parseDayOfWeek(String dayStr) {
        String[] days = {"星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"};
        for (int i = 0; i < days.length; i++) {
            if (dayStr.contains(days[i])) return i + 1;
        }
        return 1;
    }

    private String safeStr(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : "";
    }

    private String safeStr(Map<String, Object> m, String key, String defaultVal) {
        Object v = m.get(key);
        return v != null && !v.toString().isEmpty() ? v.toString() : defaultVal;
    }

    /** 从 user 表推断 teacher 表的真实 ID */
    private Long getRealTeacherId(Long userId) {
        try {
            Map<String, Object> u = jdbc.queryForMap("SELECT real_name FROM user WHERE id = ?", userId);
            String realName = (String) u.get("real_name");
            return jdbc.queryForObject(
                "SELECT id FROM teacher WHERE real_name = ? LIMIT 1", Long.class, realName);
        } catch (Exception e) {
            return userId; // fallback
        }
    }

    /** 校验班级是否归属当前教师（通过 course_class + course 关联） */
    private boolean classBelongsToTeacher(Long classId, Long teacherId) {
        try {
            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM course_class cc JOIN course c ON c.id = cc.course_id " +
                "WHERE cc.class_id = ? AND c.teacher_id = ?", Integer.class, classId, teacherId);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** 校验课程是否归属当前教师 */
    private boolean courseBelongsToTeacher(Long courseId, Long teacherId) {
        try {
            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM course WHERE id = ? AND teacher_id = ?",
                Integer.class, courseId, teacherId);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** 判断当前登录用户是否为管理员 */
    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}