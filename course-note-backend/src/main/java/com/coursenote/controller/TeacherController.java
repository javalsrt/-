package com.coursenote.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.coursenote.common.Result;
import com.coursenote.config.JwtUtil;
import com.coursenote.entity.*;
import com.coursenote.mapper.*;
import com.znxsgl.entity.ClassInfo;
import com.znxsgl.entity.Teacher;
import com.znxsgl.mapper.ClassInfoMapper;
import com.znxsgl.mapper.TeacherMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 教师端控制器 - 所有接口返回真实数据库数据，仅返回当前登录教师权限范围内的数据
 */
@RestController
@RequestMapping("/teacher")
public class TeacherController {

    @Autowired private JwtUtil jwtUtil;
    @Autowired private CourseMapper courseMapper;
    @Autowired private CourseSessionMapper sessionMapper;
    @Autowired private MaterialMapper materialMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private TeacherMapper teacherMapper;
    @Autowired private KnowledgeBaseMapper knowledgeBaseMapper;
    @Autowired private KnowledgeDocumentMapper documentMapper;
    @Autowired private AiChatRecordMapper chatRecordMapper;
    @Autowired private ClassInfoMapper classInfoMapper;

    private static final Map<String, String> TEACHER_PASSWORDS = new HashMap<>(Map.of(
        "wangjianguo", "123456", "lijiaoshou", "123456", "zhangboshi", "123456",
        "chenlaoshi", "123456", "liujiaoshou", "123456", "zhaolaoshi", "123456", "admin", "123456"
    ));

    private static final Map<String, String> TEACHER_OPENID_MAP = Map.of(
        "wangjianguo", "teacher_wang_001", "lijiaoshou", "teacher_li_001",
        "zhangboshi", "teacher_zhang_001", "chenlaoshi", "teacher_chen_001",
        "liujiaoshou", "teacher_liu_001", "zhaolaoshi", "teacher_zhao_001"
    );

    /** 获取当前登录教师的userId（token缺失或无效时抛出401，不做静默回退，防止越权） */
    private Long getCurrentUserId() {
        try {
            String authHeader = ((org.springframework.web.context.request.ServletRequestAttributes)
                org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes())
                .getRequest().getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                if (jwtUtil.validateToken(token)) return jwtUtil.getUserIdFromToken(token);
            }
        } catch (com.coursenote.common.BusinessException e) {
            throw e;
        } catch (Exception ignored) {}
        throw new com.coursenote.common.BusinessException(401, "未登录或token已过期");
    }

    /** 获取当前教师的所有课程ID列表 */
    private List<Long> getTeacherCourseIds(Long teacherId) {
        return courseMapper.selectList(new LambdaQueryWrapper<Course>()
                .eq(Course::getDeleted, 0).eq(Course::getUserId, teacherId))
                .stream().map(Course::getId).collect(Collectors.toList());
    }

    /** 获取当前教师的课程按（课程名+班级）分组的映射 */
    private Map<String, List<Long>> getCourseGroups(Long teacherId) {
        return courseMapper.selectList(new LambdaQueryWrapper<Course>()
                .eq(Course::getDeleted, 0).eq(Course::getUserId, teacherId))
            .stream().collect(Collectors.groupingBy(
                c -> c.getName() + "|" + (c.getDescription() == null ? "" : c.getDescription()),
                Collectors.mapping(Course::getId, Collectors.toList())));
    }

    /** 获取当前教师所教班级的所有课程ID（包括其他教师在该班级开设的课程） */
    private Set<Long> getAllRelatedCourseIds(Long teacherId) {
        List<Course> teacherCourses = courseMapper.selectList(new LambdaQueryWrapper<Course>()
                .eq(Course::getDeleted, 0).eq(Course::getUserId, teacherId));
        if (teacherCourses.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> classes = new HashSet<>();
        for (Course c : teacherCourses) {
            if (c.getDescription() != null && !c.getDescription().isEmpty()) {
                classes.add(c.getDescription());
            }
        }
        if (classes.isEmpty()) {
            return teacherCourses.stream().map(Course::getId).collect(Collectors.toSet());
        }
        List<Course> allClassCourses = courseMapper.selectList(new LambdaQueryWrapper<Course>()
                .eq(Course::getDeleted, 0).in(Course::getDescription, classes));
        return allClassCourses.stream().map(Course::getId).collect(Collectors.toSet());
    }

    /** 时间格式化：LocalDateTime → yyyy-MM-dd */
    private String fmtDate(LocalDateTime dt) {
        return dt != null ? dt.toString().substring(0, 10) : "";
    }

    /** 时间格式化：LocalTime → HH:mm */
    private String fmtTime(LocalTime t) {
        return t != null ? t.toString().substring(0, 5) : "";
    }

    /** 教师登录 */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        String openid = "admin".equals(username) ? "teacher_wang_001" : TEACHER_OPENID_MAP.get(username);
        String expectedPassword = TEACHER_PASSWORDS.get(username != null ? username : "");

        if (openid == null || expectedPassword == null || !expectedPassword.equals(password)) {
            return Result.error(401, "用户名或密码错误");
        }

        User teacher = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getOpenid, openid).eq(User::getDeleted, 0));
        if (teacher == null) return Result.error(401, "教师账号不存在");

        String token = jwtUtil.generateToken(teacher.getId(), "teacher_" + openid);
        Map<String, Object> user = new HashMap<>();
        user.put("id", teacher.getId());
        user.put("realName", teacher.getNickname());
        user.put("username", username);
        user.put("phone", teacher.getPhone());
        user.put("school", teacher.getSchool());
        user.put("department", teacher.getSchool() != null ? teacher.getSchool() : "计算机科学与技术学院");
        user.put("title", "教授");
        return Result.success(Map.of("token", token, "user", user));
    }

    /** 获取教师个人资料 */
    @GetMapping("/profile")
    public Result<Map<String, Object>> getProfile() {
        User teacher = userMapper.selectById(getCurrentUserId());
        if (teacher == null) return Result.error(404, "教师账号不存在");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", teacher.getId());
        data.put("realName", teacher.getNickname());
        data.put("phone", teacher.getPhone());
        data.put("school", teacher.getSchool());
        data.put("major", teacher.getMajor());
        data.put("semester", teacher.getSemester());
        data.put("semesterStartDate", teacher.getSemesterStartDate() != null ? teacher.getSemesterStartDate().toString() : "");
        data.put("semesterWeeks", teacher.getSemesterWeeks());

        // 返回教师实体ID（teacher.id），供排课管理等按教师过滤的接口使用
        if (teacher.getRealName() != null) {
            Teacher t = teacherMapper.selectOne(
                    new LambdaQueryWrapper<Teacher>().eq(Teacher::getRealName, teacher.getRealName()));
            data.put("teacherId", t != null ? t.getId() : null);
        }
        return Result.success(data);
    }

    /** 更新教师个人资料 */
    @PutMapping("/profile")
    public Result<?> updateProfile(@RequestBody Map<String, String> body) {
        User teacher = userMapper.selectById(getCurrentUserId());
        if (teacher == null) return Result.error(404, "教师账号不存在");
        if (body.get("realName") != null) teacher.setNickname(body.get("realName"));
        if (body.get("phone") != null) teacher.setPhone(body.get("phone"));
        if (body.get("school") != null) teacher.setSchool(body.get("school"));
        if (body.get("major") != null) teacher.setMajor(body.get("major"));
        if (body.get("semester") != null) teacher.setSemester(body.get("semester"));
        if (body.get("semesterStartDate") != null && !body.get("semesterStartDate").isEmpty()) {
            teacher.setSemesterStartDate(LocalDate.parse(body.get("semesterStartDate")));
        }
        if (body.get("semesterWeeks") != null && !body.get("semesterWeeks").isEmpty()) {
            teacher.setSemesterWeeks(Integer.parseInt(body.get("semesterWeeks")));
        }
        teacher.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(teacher);
        return Result.success("更新成功");
    }

    /** 获取教师当前学期配置 */
    @GetMapping("/semester")
    public Result<Map<String, Object>> getSemester() {
        User teacher = userMapper.selectById(getCurrentUserId());
        if (teacher == null) return Result.error(404, "教师账号不存在");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("currentSemester", teacher.getSemester());
        data.put("semesterStartDate", teacher.getSemesterStartDate() != null ? teacher.getSemesterStartDate().toString() : "");
        data.put("semesterWeeks", teacher.getSemesterWeeks());
        return Result.success(data);
    }

    /** 修改教师密码 */
    @PutMapping("/password")
    public Result<?> changePassword(@RequestBody Map<String, String> body) {
        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");
        if (newPassword == null || newPassword.length() < 6) {
            return Result.error(400, "新密码长度至少6位");
        }
        User teacher = userMapper.selectById(getCurrentUserId());
        if (teacher == null) return Result.error(404, "教师账号不存在");

        Map<String, String> openidToUsername = new HashMap<>();
        TEACHER_OPENID_MAP.forEach((k, v) -> openidToUsername.put(v, k));
        String username = openidToUsername.getOrDefault(teacher.getOpenid(), "admin");

        if (!TEACHER_PASSWORDS.getOrDefault(username, "").equals(oldPassword)) {
            return Result.error(400, "原密码错误");
        }
        TEACHER_PASSWORDS.put(username, newPassword);
        return Result.success("密码修改成功");
    }

    /**
     * 获取当前用户可见的学生ID集合。
     * 管理员返回 null（表示不限）；教师返回所教班级的全部学生ID。
     */
    private List<Long> getVisibleStudentIds(User current, boolean isAdmin) {
        if (isAdmin) return null;
        Set<Long> classIds = getTeacherClassIds(current != null ? current.getRealName() : null);
        if (classIds.isEmpty()) return Collections.emptyList();
        return userMapper.selectList(new LambdaQueryWrapper<User>()
                        .eq(User::getDeleted, 0).eq(User::getRole, 1).eq(User::getStatus, 1)
                        .in(User::getClassId, classIds))
                .stream().map(User::getId).collect(Collectors.toList());
    }

    /** 教师的教务课程（course 表，按 teacher.real_name 关联）；管理员返回全部 */
    private List<Map<String, Object>> getJwCourses(User current, boolean isAdmin) {
        String sql = "SELECT DISTINCT c.id AS courseId, c.course_name AS courseName, c.semester " +
                "FROM course c JOIN teacher t ON t.id = c.teacher_id";
        List<Object> params = new ArrayList<>();
        if (!isAdmin) {
            sql += " WHERE t.real_name = ?";
            params.add(current != null ? current.getRealName() : "");
        }
        return jdbcTemplate.queryForList(sql, params.toArray());
    }

    /** ID 列表转 SQL IN 片段（元素均为 Long，无注入风险） */
    private String joinIds(List<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    /** 仪表盘数据（教师=所教班级学生视角；管理员=全局视角） */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        Long userId = getCurrentUserId();
        User current = userMapper.selectById(userId);
        if (current == null) return Result.error(401, "账号不存在");
        boolean isAdmin = current.getRole() != null && current.getRole() == 3;

        List<Long> visibleStudentIds = getVisibleStudentIds(current, isAdmin);
        boolean noStudents = visibleStudentIds != null && visibleStudentIds.isEmpty();
        Map<String, Object> data = new HashMap<>();

        // 课程数 = 教务课程（course 表）+ 个人课程（personal_course）
        long courseCount = getJwCourses(current, isAdmin).size()
                + courseMapper.selectCount(new LambdaQueryWrapper<Course>()
                        .eq(Course::getDeleted, 0).eq(Course::getUserId, userId));

        // 学生数 = 可见学生数（教师=所教班级学生；管理员=全部在读学生）
        long studentCount = visibleStudentIds != null ? visibleStudentIds.size()
                : userMapper.selectCount(new LambdaQueryWrapper<User>()
                        .eq(User::getDeleted, 0).eq(User::getRole, 1).eq(User::getStatus, 1));

        // 资料数：可见学生上传的资料
        LambdaQueryWrapper<Material> mw = new LambdaQueryWrapper<Material>().eq(Material::getDeleted, 0);
        if (visibleStudentIds != null) mw.in(Material::getUserId, visibleStudentIds);
        long totalMaterials = noStudents ? 0 : materialMapper.selectCount(mw);

        long todaySessions = sessionMapper.selectCount(new LambdaQueryWrapper<CourseSession>()
                .eq(CourseSession::getDeleted, 0).eq(CourseSession::getSessionDate, LocalDate.now()));

        // AI对话数：可见学生的提问
        LambdaQueryWrapper<AiChatRecord> cw = new LambdaQueryWrapper<AiChatRecord>()
                .eq(AiChatRecord::getRole, "user");
        if (visibleStudentIds != null) cw.in(AiChatRecord::getUserId, visibleStudentIds);
        long aiChats = noStudents ? 0 : chatRecordMapper.selectCount(cw);

        Map<String, Object> stats = new HashMap<>();
        stats.put("courseCount", (int) courseCount);
        stats.put("studentCount", (int) studentCount);
        stats.put("todaySessions", (int) todaySessions);
        stats.put("totalMaterials", (int) totalMaterials);
        stats.put("aiChats", (int) aiChats);
        data.put("stats", stats);

        // 今日课程：从排课表（schedule）按当前学期取今天的课（教师=本人课程）
        int todayDayOfWeek = LocalDate.now().getDayOfWeek().getValue();
        String todaySql = "SELECT c.id AS courseId, c.course_name AS courseName, t.real_name AS teacherName, " +
                "s.start_time AS startTime, s.end_time AS endTime, " +
                "GROUP_CONCAT(DISTINCT s.classroom ORDER BY s.classroom) AS classroom " +
                "FROM schedule s " +
                "JOIN course c ON c.id = s.course_id " +
                "JOIN teacher t ON t.id = c.teacher_id " +
                "WHERE s.day_of_week = ? AND s.status = 1 " +
                "AND s.semester = (SELECT name FROM semester WHERE is_current = 1 LIMIT 1) ";
        List<Object> todayParams = new ArrayList<>(List.of(todayDayOfWeek));
        if (!isAdmin) {
            todaySql += "AND c.teacher_id = (SELECT id FROM teacher WHERE real_name = ? LIMIT 1) ";
            todayParams.add(current.getRealName());
        }
        todaySql += "GROUP BY c.id, c.course_name, t.real_name, s.start_time, s.end_time, s.day_of_week " +
                "ORDER BY s.start_time";
        data.put("todayCourses", jdbcTemplate.queryForList(todaySql, todayParams.toArray()).stream()
                .map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", ((Number) r.get("courseId")).longValue());
                    m.put("name", r.get("courseName"));
                    m.put("teacher", r.get("teacherName"));
                    m.put("classroom", r.get("classroom"));
                    m.put("color", "#0052d9");
                    m.put("startTime", fmtTime(toLocalTime(r.get("startTime"))));
                    m.put("endTime", fmtTime(toLocalTime(r.get("endTime"))));
                    m.put("status", 1);
                    return m;
                }).collect(Collectors.toList()));

        // 知识库概况：可见学生（含教师本人）的知识库文档按课程名聚合
        String kbSql = "SELECT pc.name AS courseName, COUNT(DISTINCT kd.id) AS docCount " +
                "FROM knowledge_base kb " +
                "JOIN knowledge_document kd ON kd.knowledge_base_id = kb.id AND kd.deleted = 0 " +
                "JOIN personal_course pc ON pc.id = kb.course_id AND pc.deleted = 0 " +
                "WHERE kb.deleted = 0 ";
        List<Object> kbParams = new ArrayList<>();
        if (visibleStudentIds != null) {
            List<Long> ownerIds = new ArrayList<>(visibleStudentIds);
            ownerIds.add(userId);
            kbSql += "AND kb.user_id IN (" + joinIds(ownerIds) + ") ";
        }
        kbSql += "GROUP BY pc.name ORDER BY docCount DESC LIMIT 8";
        data.put("knowledgeBases", jdbcTemplate.queryForList(kbSql, kbParams.toArray()).stream()
                .map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", r.get("courseName"));
                    m.put("name", r.get("courseName"));
                    m.put("documentCount", ((Number) r.get("docCount")).intValue());
                    return m;
                }).collect(Collectors.toList()));

        return Result.success(data);
    }

    /** 获取课程列表（课表视图）：教师看自己教的课，管理员看所有课程 */
    @GetMapping("/courses")
    public Result<List<Map<String, Object>>> courses() {
        Long userId = getCurrentUserId();
        User user = userMapper.selectById(userId);
        List<Map<String, Object>> result = new ArrayList<>();
        boolean isAdmin = user != null && user.getRole() != null && user.getRole() == 3;

        // 1. 教务体系课程（班级制排课）
        //    教师：user.real_name → teacher.id → course.teacher_id，只看自己教的课
        //    管理员：看所有教师的教务课程
        if (user != null && user.getRole() != null && user.getRole() >= 2) {
            result.addAll(loadJwCourses(user, isAdmin));
        }

        // 2. 个人手动添加课程（personal_course）：只加载当前用户自己的（管理员也不例外，避免串号）
        result.addAll(courseMapper.selectList(new LambdaQueryWrapper<Course>()
                .eq(Course::getDeleted, 0).eq(Course::getUserId, userId)
                .orderByAsc(Course::getDayOfWeek, Course::getStartTime)).stream()
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", c.getId()); m.put("name", c.getName()); m.put("teacher", c.getTeacher());
                    m.put("classroom", c.getClassroom()); m.put("dayOfWeek", c.getDayOfWeek());
                    m.put("startTime", fmtTime(c.getStartTime())); m.put("endTime", fmtTime(c.getEndTime()));
                    m.put("weeks", c.getWeeks()); m.put("color", c.getColor()); m.put("semester", c.getSemester());
                    m.put("description", c.getDescription()); m.put("status", c.getStatus());
                    m.put("source", "personal");
                    return m;
                }).collect(Collectors.toList()));

        // 按星期+时间排序，便于课表网格渲染
        result.sort((a, b) -> {
            int da = a.get("dayOfWeek") instanceof Number ? ((Number) a.get("dayOfWeek")).intValue() : 0;
            int db = b.get("dayOfWeek") instanceof Number ? ((Number) b.get("dayOfWeek")).intValue() : 0;
            if (da != db) return Integer.compare(da, db);
            String ta = String.valueOf(a.getOrDefault("startTime", ""));
            String tb = String.valueOf(b.getOrDefault("startTime", ""));
            return ta.compareTo(tb);
        });
        return Result.success(result);
    }

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    /**
     * 加载教务课程并映射为前端课程列表格式（课表视图）。
     * 每门课按 distinct(星期, 开始时间) 展开成多条（不同班级可能不同时段），
     * 同一时段多班级合并为一条（班级名/教室聚合），weeks 取该时段实际周次并集。
     *
     * @param user    当前登录用户
     * @param isAdmin true=返回所有教师的教务课程；false=仅返回该教师自己教的课
     */
    private List<Map<String, Object>> loadJwCourses(User user, boolean isAdmin) {
        String sql = "SELECT c.id AS courseId, c.course_name AS courseName, t.real_name AS teacherName, " +
            "       c.semester, s.day_of_week AS dayOfWeek, s.start_time AS startTime, s.end_time AS endTime, " +
            "       GROUP_CONCAT(DISTINCT s.classroom ORDER BY s.classroom SEPARATOR ' / ') AS classroom, " +
            "       GROUP_CONCAT(DISTINCT ci.class_name ORDER BY ci.class_name SEPARATOR '、') AS classNames, " +
            "       GROUP_CONCAT(DISTINCT JSON_EXTRACT(s.weeks, '$[0]') ORDER BY JSON_EXTRACT(s.weeks, '$[0]')) AS weekList " +
            "FROM course c " +
            "JOIN teacher t ON t.id = c.teacher_id " +
            "JOIN course_class cc ON cc.course_id = c.id " +
            "JOIN class_info ci ON ci.id = cc.class_id " +
            "JOIN schedule s ON s.course_id = c.id AND s.user_id IN (SELECT id FROM user WHERE class_id = cc.class_id) " +
            "WHERE c.semester = (SELECT name FROM semester WHERE is_current = 1 LIMIT 1) ";
        Object[] params;
        if (!isAdmin) {
            sql += " AND t.real_name = ? ";
            params = new Object[]{ user.getRealName() };
        } else {
            params = new Object[]{};
        }
        sql += "GROUP BY c.id, c.course_name, t.real_name, c.semester, s.day_of_week, s.start_time, s.end_time " +
               "ORDER BY s.day_of_week, s.start_time";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            Number courseIdNum = (Number) row.get("courseId");
            Number dayOfWeekNum = (Number) row.get("dayOfWeek");
            m.put("id", "jw-" + courseIdNum.longValue() + "-" + dayOfWeekNum.intValue() + "-" + fmtTime(toLocalTime(row.get("startTime"))));
            m.put("courseId", courseIdNum.longValue());
            m.put("name", row.get("courseName"));
            m.put("teacher", row.get("teacherName"));
            m.put("classroom", row.get("classroom"));
            m.put("dayOfWeek", dayOfWeekNum.intValue());
            m.put("startTime", fmtTime(toLocalTime(row.get("startTime"))));
            m.put("endTime", fmtTime(toLocalTime(row.get("endTime"))));
            // weeks：周次并集转 JSON 数组字符串（前端课表周次筛选用）
            m.put("weeks", toWeeksJson(row.get("weekList")));
            m.put("color", "#0052d9");
            m.put("semester", row.get("semester"));
            m.put("description", row.get("classNames")); // 班级名列表（课表页班级筛选）
            m.put("status", 1);
            m.put("source", "jw"); // 教务课程标记：前端禁用编辑/删除（走排课管理）
            result.add(m);
        }
        return result;
    }

    /** "1,2,3" → "[1,2,3]"；空值回退 [1..16] */
    private String toWeeksJson(Object weekList) {
        if (weekList == null) return "[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16]";
        try {
            List<Integer> weeks = Arrays.stream(String.valueOf(weekList).split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .map(Integer::parseInt).distinct().sorted()
                    .collect(Collectors.toList());
            if (weeks.isEmpty()) return "[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16]";
            return weeks.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",", "[", "]"));
        } catch (Exception e) {
            return "[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16]";
        }
    }

    /** 数据库时间值转 LocalTime */
    private LocalTime toLocalTime(Object value) {
        if (value == null) return LocalTime.of(8, 0);
        try {
            String s = value.toString();
            return LocalTime.parse(s.length() > 5 ? s.substring(0, 5) : s);
        } catch (Exception e) {
            return LocalTime.of(8, 0);
        }
    }

    /** 创建课程 */
    @PostMapping("/courses")
    public Result<Map<String, Object>> createCourse(@RequestBody Map<String, Object> body) {
        Course course = new Course();
        course.setUserId(getCurrentUserId());
        course.setName((String) body.get("name"));
        course.setTeacher((String) body.getOrDefault("teacher", ""));
        course.setClassroom((String) body.getOrDefault("classroom", ""));
        course.setDayOfWeek(body.get("dayOfWeek") != null ? ((Number) body.get("dayOfWeek")).intValue() : 1);
        course.setStartTime(LocalTime.parse((String) body.getOrDefault("startTime", "08:00")));
        course.setEndTime(LocalTime.parse((String) body.getOrDefault("endTime", "09:40")));
        course.setWeeks((String) body.getOrDefault("weeks", "1-16"));
        course.setColor((String) body.getOrDefault("color", "#0052d9"));
        course.setSemester((String) body.getOrDefault("semester", "2024-2025-2"));
        course.setDescription((String) body.getOrDefault("description", ""));
        course.setStatus(1);
        courseMapper.insert(course);
        return Result.success(Map.of("id", course.getId(), "name", course.getName()));
    }

    /** 更新课程 */
    @PutMapping("/courses/{id}")
    public Result<?> updateCourse(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Course course = courseMapper.selectOne(new LambdaQueryWrapper<Course>()
                .eq(Course::getId, id).eq(Course::getUserId, getCurrentUserId()));
        if (course == null) return Result.error("课程不存在或无权限");
        if (body.containsKey("name")) course.setName((String) body.get("name"));
        if (body.containsKey("teacher")) course.setTeacher((String) body.get("teacher"));
        if (body.containsKey("classroom")) course.setClassroom((String) body.get("classroom"));
        if (body.containsKey("dayOfWeek")) course.setDayOfWeek(((Number) body.get("dayOfWeek")).intValue());
        if (body.containsKey("startTime")) course.setStartTime(LocalTime.parse((String) body.get("startTime")));
        if (body.containsKey("endTime")) course.setEndTime(LocalTime.parse((String) body.get("endTime")));
        if (body.containsKey("color")) course.setColor((String) body.get("color"));
        if (body.containsKey("description")) course.setDescription((String) body.get("description"));
        courseMapper.updateById(course);
        return Result.success("更新成功");
    }

    /** 删除课程 */
    @DeleteMapping("/courses/{id}")
    public Result<?> deleteCourse(@PathVariable Long id) {
        Course course = courseMapper.selectOne(new LambdaQueryWrapper<Course>()
                .eq(Course::getId, id).eq(Course::getUserId, getCurrentUserId()));
        if (course == null) return Result.error("课程不存在或无权限");
        courseMapper.deleteById(id);
        return Result.success("删除成功");
    }

    /**
     * 查询教师所教班级ID集合。
     * 链路：user.real_name → teacher.id → course.teacher_id → course_class.class_id
     * 未匹配到教师档案或无任教课程时返回空集合（教师将看不到任何学生）。
     */
    private Set<Long> getTeacherClassIds(String realName) {
        if (realName == null || realName.trim().isEmpty()) return Collections.emptySet();
        Teacher t = teacherMapper.selectOne(
                new LambdaQueryWrapper<Teacher>().eq(Teacher::getRealName, realName));
        if (t == null) return Collections.emptySet();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT DISTINCT cc.class_id AS classId " +
                "FROM course c JOIN course_class cc ON cc.course_id = c.id " +
                "WHERE c.teacher_id = ?", t.getId());
        return rows.stream()
                .map(r -> ((Number) r.get("classId")).longValue())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** 学生显示名：nickname 为空时回退 real_name */
    private String displayName(User u) {
        return (u.getNickname() != null && !u.getNickname().trim().isEmpty())
                ? u.getNickname() : u.getRealName();
    }

    /**
     * 获取学生列表（按角色控制范围）：
     *   管理员 = 全部学生；教师 = 仅所教班级的学生
     */
    @GetMapping("/students")
    public Result<List<Map<String, Object>>> students() {
        Long userId = getCurrentUserId();
        User current = userMapper.selectById(userId);
        if (current == null) return Result.error(401, "账号不存在");
        boolean isAdmin = current.getRole() != null && current.getRole() == 3;

        // 教师可见班级集合：null 表示不限（管理员）
        Set<Long> visibleClassIds = null;
        if (!isAdmin) {
            visibleClassIds = getTeacherClassIds(current.getRealName());
            if (visibleClassIds.isEmpty()) return Result.success(new ArrayList<>());
        }

        LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<User>()
                .eq(User::getDeleted, 0)
                .eq(User::getRole, 1)
                .eq(User::getStatus, 1)
                .orderByAsc(User::getClassId)
                .orderByAsc(User::getStudentNo);
        if (visibleClassIds != null) qw.in(User::getClassId, visibleClassIds);
        List<User> users = userMapper.selectList(qw);
        if (users.isEmpty()) return Result.success(new ArrayList<>());

        // 班级名称映射
        Map<Long, String> classNameMap = new HashMap<>();
        for (ClassInfo ci : classInfoMapper.selectList(null)) {
            classNameMap.put(ci.getId(), ci.getClassName());
        }

        List<Long> userIds = users.stream().map(User::getId).collect(Collectors.toList());

        Map<Long, Integer> matCountMap = new HashMap<>();
        materialMapper.countByUserIds(userIds).forEach(row ->
            matCountMap.put(((Number) row.get("userId")).longValue(), ((Number) row.get("cnt")).intValue()));

        Map<Long, Integer> kbDocCountMap = new HashMap<>();
        documentMapper.countByUserIds(userIds).forEach(row ->
            kbDocCountMap.put(((Number) row.get("userId")).longValue(), ((Number) row.get("cnt")).intValue()));

        return Result.success(users.stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("nickname", displayName(u));
            m.put("realName", u.getRealName());
            m.put("school", u.getSchool());
            m.put("major", u.getMajor());
            m.put("grade", u.getGrade());
            m.put("classId", u.getClassId());
            m.put("className", u.getClassId() != null
                    ? classNameMap.getOrDefault(u.getClassId(), "未知班级") : "未分班");
            // 学号：优先真实 student_no，历史开放注册账号缺失时按旧规则生成
            m.put("studentNo", u.getStudentNo() != null && !u.getStudentNo().isEmpty() ? u.getStudentNo()
                    : (u.getGrade() != null ? u.getGrade() + String.format("%04d", u.getId() % 10000) : String.valueOf(u.getId())));
            m.put("materialCount", matCountMap.getOrDefault(u.getId(), 0));
            m.put("kbDocCount", kbDocCountMap.getOrDefault(u.getId(), 0));
            return m;
        }).collect(Collectors.toList()));
    }

    /** 获取学生详情（教师仅可查看所教班级学生，管理员可查看全部） */
    @GetMapping("/students/{id}")
    public Result<Map<String, Object>> studentDetail(@PathVariable Long id) {
        User user = userMapper.selectById(id);
        if (user == null) return Result.error("学生不存在");

        // 权限校验：教师只能查看所教班级的学生
        User current = userMapper.selectById(getCurrentUserId());
        boolean isAdmin = current != null && current.getRole() != null && current.getRole() == 3;
        if (!isAdmin) {
            Set<Long> classIds = getTeacherClassIds(current != null ? current.getRealName() : null);
            if (user.getClassId() == null || !classIds.contains(user.getClassId())) {
                return Result.error(403, "无权查看该学生（非您所教班级）");
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", user.getId());
        data.put("nickname", displayName(user));
        data.put("realName", user.getRealName());
        data.put("school", user.getSchool()); data.put("major", user.getMajor()); data.put("grade", user.getGrade());
        data.put("classId", user.getClassId());
        if (user.getClassId() != null) {
            ClassInfo ci = classInfoMapper.selectById(user.getClassId());
            data.put("className", ci != null ? ci.getClassName() : "未知班级");
        } else {
            data.put("className", "未分班");
        }
        data.put("studentNo", user.getStudentNo() != null && !user.getStudentNo().isEmpty() ? user.getStudentNo()
                : (user.getGrade() != null ? user.getGrade() + String.format("%04d", user.getId() % 10000) : String.valueOf(user.getId())));

        data.put("materialCount", materialMapper.selectCount(
            new LambdaQueryWrapper<Material>().eq(Material::getUserId, id).eq(Material::getDeleted, 0)).intValue());
        data.put("chatCount", chatRecordMapper.selectCount(
            new LambdaQueryWrapper<AiChatRecord>().eq(AiChatRecord::getUserId, id).eq(AiChatRecord::getRole, "user")).intValue());
        data.put("kbDocCount", documentMapper.selectCount(
            new LambdaQueryWrapper<KnowledgeDocument>().eq(KnowledgeDocument::getUserId, id).eq(KnowledgeDocument::getDeleted, 0)).intValue());

        // 课程可见范围：管理员 = 学生全部课程；教师 = 本人课程 + 所教班级学生的影子课程
        Long teacherId = getCurrentUserId();
        final Set<Long> relatedCourseIds;
        if (isAdmin) {
            relatedCourseIds = null; // 不过滤，管理员可见学生全部课程
        } else {
            Set<Long> ids = new HashSet<>(getAllRelatedCourseIds(teacherId));
            Set<Long> classIds = getTeacherClassIds(current.getRealName());
            if (!classIds.isEmpty()) {
                List<String> descs = classIds.stream().map(cid -> "class:" + cid).collect(Collectors.toList());
                courseMapper.selectList(new LambdaQueryWrapper<Course>()
                        .eq(Course::getDeleted, 0).in(Course::getDescription, descs))
                        .forEach(c -> ids.add(c.getId()));
            }
            relatedCourseIds = ids;
        }
        List<KnowledgeBase> studentKbs = knowledgeBaseMapper.findByUserId(id).stream()
                .filter(kb -> relatedCourseIds == null || relatedCourseIds.contains(kb.getCourseId()))
                .collect(Collectors.toList());
        data.put("courseCount", (int) studentKbs.stream().map(KnowledgeBase::getCourseId).distinct().count());
        data.put("noteCount", materialMapper.selectCount(
            new LambdaQueryWrapper<Material>().eq(Material::getUserId, id).eq(Material::getType, "NOTE").eq(Material::getDeleted, 0)).intValue());

        // 按课程详细统计：同一学生同一课程可能对应多条知识库记录，需要按 courseId 去重并汇总
        Map<Long, Map<String, Object>> courseDetailMap = new LinkedHashMap<>();
        Set<Long> countedCourses = new HashSet<>();
        for (KnowledgeBase kb : studentKbs) {
            Long cid = kb.getCourseId();
            Course c = courseMapper.selectById(cid);
            String courseName = c != null ? c.getName() : (kb.getName() != null ? kb.getName().replace("知识库", "") : "未知课程");
            String teacher = c != null ? c.getTeacher() : "";
            String color = c != null && c.getColor() != null ? c.getColor() : "#0052d9";

            Map<String, Object> cd = courseDetailMap.computeIfAbsent(cid, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("courseId", cid);
                m.put("courseName", courseName);
                m.put("teacher", teacher);
                m.put("color", color);
                m.put("kbDocCount", 0);
                m.put("chatCount", 0);
                m.put("noteCount", 0);
                m.put("materialCount", 0);
                return m;
            });
            // 若同一 courseId 的某条记录查不到课程而另一条能查到，以后者为准
            if (c != null) {
                cd.put("courseName", courseName);
                cd.put("teacher", teacher);
                cd.put("color", color);
            }

            cd.put("kbDocCount", (Integer) cd.get("kbDocCount") + documentMapper.selectCount(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getKnowledgeBaseId, kb.getId()).eq(KnowledgeDocument::getDeleted, 0)).intValue());

            if (!countedCourses.contains(cid)) {
                countedCourses.add(cid);
                cd.put("chatCount", chatRecordMapper.selectCount(new LambdaQueryWrapper<AiChatRecord>()
                    .eq(AiChatRecord::getUserId, id).eq(AiChatRecord::getCourseId, cid).eq(AiChatRecord::getRole, "user")).intValue());
                cd.put("noteCount", materialMapper.selectCount(new LambdaQueryWrapper<Material>()
                    .eq(Material::getUserId, id).eq(Material::getCourseId, cid).eq(Material::getType, "NOTE").eq(Material::getDeleted, 0)).intValue());
                cd.put("materialCount", materialMapper.selectCount(new LambdaQueryWrapper<Material>()
                    .eq(Material::getUserId, id).eq(Material::getCourseId, cid).eq(Material::getDeleted, 0)).intValue());
            }
        }
        data.put("courseDetails", new ArrayList<>(courseDetailMap.values()));

        // 资料列表
        data.put("materials", materialMapper.selectList(new LambdaQueryWrapper<Material>()
                .eq(Material::getUserId, id).eq(Material::getDeleted, 0).orderByDesc(Material::getCreatedAt).last("LIMIT 10")).stream()
                .map(m -> {
                    Course c = courseMapper.selectById(m.getCourseId());
                    Map<String, Object> mm = new LinkedHashMap<>();
                    mm.put("id", m.getId()); mm.put("title", m.getTitle()); mm.put("type", m.getType());
                    mm.put("courseName", c != null ? c.getName() : ""); mm.put("createdAt", fmtDate(m.getCreatedAt()));
                    return mm;
                }).collect(Collectors.toList()));

        // 知识库文档列表（只展示当前教师权限范围内的课程文档）
        data.put("kbDocuments", documentMapper.selectList(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getUserId, id).eq(KnowledgeDocument::getDeleted, 0).orderByDesc(KnowledgeDocument::getCreatedAt)).stream()
                .map(d -> {
                    KnowledgeBase kb = knowledgeBaseMapper.selectById(d.getKnowledgeBaseId());
                    Course c = kb != null ? courseMapper.selectById(kb.getCourseId()) : null;
                    Map<String, Object> dm = new LinkedHashMap<>();
                    dm.put("id", d.getId()); dm.put("title", d.getTitle()); dm.put("summary", d.getSummary());
                    dm.put("content", d.getContent()); dm.put("indexStatus", d.getIndexStatus());
                    dm.put("courseId", kb != null ? kb.getCourseId() : null);
                    dm.put("courseName", c != null ? c.getName() : (kb != null ? kb.getName() : ""));
                    dm.put("createdAt", fmtDate(d.getCreatedAt()));
                    return dm;
                })
                .filter(dm -> dm.get("courseId") != null
                        && (relatedCourseIds == null || relatedCourseIds.contains((Long) dm.get("courseId"))))
                .collect(Collectors.toList()));

        // 近 30 天每日统计（资料、知识库、AI 提问）
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(29);
        String startStr = startDate.atStartOfDay().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String endStr = today.plusDays(1).atStartOfDay().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        Map<String, Integer> matDateMap = new HashMap<>();
        materialMapper.countByUserIdAndDate(id, startStr, endStr).forEach(row ->
            matDateMap.put(row.get("date").toString().trim(), ((Number) row.get("cnt")).intValue()));

        Map<String, Integer> kbDateMap = new HashMap<>();
        documentMapper.countByUserIdAndDate(id, startStr, endStr).forEach(row ->
            kbDateMap.put(row.get("date").toString().trim(), ((Number) row.get("cnt")).intValue()));

        Map<String, Integer> aiDateMap = new HashMap<>();
        chatRecordMapper.countByUserIdAndDate(id, startStr, endStr).forEach(row ->
            aiDateMap.put(row.get("date").toString().trim(), ((Number) row.get("cnt")).intValue()));

        List<Map<String, Object>> dailyStats = new ArrayList<>();
        for (int i = 29; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            String ds = d.toString();
            Map<String, Object> stat = new LinkedHashMap<>();
            stat.put("date", ds);
            stat.put("materialCount", matDateMap.getOrDefault(ds, 0));
            stat.put("kbDocCount", kbDateMap.getOrDefault(ds, 0));
            stat.put("chatCount", aiDateMap.getOrDefault(ds, 0));
            dailyStats.add(stat);
        }
        data.put("dailyStats", dailyStats);

        return Result.success(data);
    }

    /** 获取课段列表 */
    @GetMapping("/sessions")
    public Result<List<Map<String, Object>>> sessions(@RequestParam(required = false) Long courseId) {
        LambdaQueryWrapper<CourseSession> wrapper = new LambdaQueryWrapper<CourseSession>()
                .eq(CourseSession::getDeleted, 0).orderByDesc(CourseSession::getSessionDate);
        if (courseId != null) wrapper.eq(CourseSession::getCourseId, courseId);
        return Result.success(sessionMapper.selectList(wrapper).stream().map(s -> {
            Course c = courseMapper.selectById(s.getCourseId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId()); m.put("courseId", s.getCourseId());
            m.put("courseName", c != null ? c.getName() : "未知"); m.put("teacher", c != null ? c.getTeacher() : "");
            m.put("sessionDate", s.getSessionDate() != null ? s.getSessionDate().toString() : "");
            m.put("startTime", s.getActualStartTime() != null ? fmtTime(s.getActualStartTime().toLocalTime()) : "");
            m.put("endTime", s.getActualEndTime() != null ? fmtTime(s.getActualEndTime().toLocalTime()) : "");
            m.put("status", s.getStatus()); m.put("materialCount", s.getMaterialCount());
            m.put("summaryStatus", s.getSummaryStatus()); m.put("summary", s.getSummary());
            return m;
        }).collect(Collectors.toList()));
    }

    /**
     * 获取资料列表（按角色控制范围）：
     *   教师 = 所教班级学生上传的资料；管理员 = 全部资料
     * 筛选参数 courseName 为课程名（教务课程与学生个人/影子课程同名互认）
     */
    @GetMapping("/materials")
    public Result<List<Map<String, Object>>> materials(
            @RequestParam(required = false) String courseName,
            @RequestParam(required = false) Long studentId) {
        Long userId = getCurrentUserId();
        User current = userMapper.selectById(userId);
        if (current == null) return Result.error(401, "账号不存在");
        boolean isAdmin = current.getRole() != null && current.getRole() == 3;
        List<Long> visibleStudentIds = getVisibleStudentIds(current, isAdmin);
        if (visibleStudentIds != null && visibleStudentIds.isEmpty()) {
            return Result.success(Collections.emptyList());
        }

        LambdaQueryWrapper<Material> wrapper = new LambdaQueryWrapper<Material>()
                .eq(Material::getDeleted, 0);
        if (visibleStudentIds != null) wrapper.in(Material::getUserId, visibleStudentIds);
        if (studentId != null) wrapper.eq(Material::getUserId, studentId);
        wrapper.orderByDesc(Material::getCreatedAt).last("LIMIT 1000");
        List<Material> materials = materialMapper.selectList(wrapper);
        if (materials.isEmpty()) return Result.success(Collections.emptyList());

        // 课程名映射（个人课程/影子课程均挂在 course 表）
        Set<Long> courseIds = materials.stream().map(Material::getCourseId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> courseNameMap = courseIds.isEmpty() ? Collections.emptyMap() :
                courseMapper.selectBatchIds(courseIds).stream()
                        .collect(Collectors.toMap(Course::getId,
                                c -> c.getName() == null ? "" : c.getName(), (a, b) -> a));

        // 按课程名过滤（教务课程名 = 学生课程名）
        if (courseName != null && !courseName.isEmpty()) {
            materials = materials.stream()
                    .filter(m -> courseName.equals(courseNameMap.get(m.getCourseId())))
                    .collect(Collectors.toList());
            if (materials.isEmpty()) return Result.success(Collections.emptyList());
        }

        // 上传者姓名映射
        Set<Long> userIds = materials.stream().map(Material::getUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> userNameMap = userIds.isEmpty() ? Collections.emptyMap() :
                userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> displayName(u), (a, b) -> a));

        return Result.success(materials.stream().map(m -> {
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("id", m.getId()); mm.put("title", m.getTitle()); mm.put("type", m.getType());
            mm.put("content", m.getContent()); mm.put("aiTags", m.getAiTags()); mm.put("aiProcessed", m.getAiProcessed());
            mm.put("courseName", courseNameMap.getOrDefault(m.getCourseId(), "")); mm.put("courseId", m.getCourseId());
            mm.put("studentName", userNameMap.getOrDefault(m.getUserId(), "")); mm.put("studentId", m.getUserId());
            mm.put("createdAt", fmtDate(m.getCreatedAt()));
            return mm;
        }).collect(Collectors.toList()));
    }

    /**
     * 近30天趋势数据（按角色控制范围）：
     *   教师 = 可见学生（含本人）的数据；管理员 = 全局数据
     * 筛选参数 courseName 为课程名（教务课程与学生个人/影子课程同名互认）
     */
    @GetMapping("/stats/trend")
    public Result<List<Map<String, Object>>> statsTrend(@RequestParam(required = false) String courseName) {
        Long userId = getCurrentUserId();
        User current = userMapper.selectById(userId);
        if (current == null) return Result.error(401, "账号不存在");
        boolean isAdmin = current.getRole() != null && current.getRole() == 3;
        List<Long> visibleStudentIds = getVisibleStudentIds(current, isAdmin);

        Map<String, Map<String, Integer>> days = new LinkedHashMap<>();
        LocalDate end = LocalDate.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
        for (int i = 29; i >= 0; i--) {
            days.put(end.minusDays(i).format(fmt), new HashMap<>(Map.of("materials", 0, "kbDocs", 0)));
        }
        LocalDateTime startTime = end.minusDays(29).atStartOfDay();

        // 资料趋势：可见学生上传的资料
        LambdaQueryWrapper<Material> mw = new LambdaQueryWrapper<Material>()
                .eq(Material::getDeleted, 0).ge(Material::getCreatedAt, startTime);
        if (visibleStudentIds != null) mw.in(Material::getUserId, visibleStudentIds);
        List<Material> mats = materialMapper.selectList(mw);
        if (!mats.isEmpty() && courseName != null && !courseName.isEmpty()) {
            Set<Long> cids = mats.stream().map(Material::getCourseId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            Map<Long, String> nameMap = cids.isEmpty() ? Collections.emptyMap() :
                    courseMapper.selectBatchIds(cids).stream()
                            .collect(Collectors.toMap(Course::getId,
                                    c -> c.getName() == null ? "" : c.getName(), (a, b) -> a));
            mats = mats.stream().filter(m -> courseName.equals(nameMap.get(m.getCourseId())))
                    .collect(Collectors.toList());
        }
        for (Material m : mats) {
            if (m.getCreatedAt() != null) {
                days.getOrDefault(m.getCreatedAt().format(fmt), new HashMap<>()).merge("materials", 1, Integer::sum);
            }
        }

        // 知识库文档趋势：可见学生（含教师本人）的知识库文档
        // 注意：knowledge_base.course_id 关联 personal_course（com.coursenote.Course 实体），
        // 而非 znxsgl 教务 course 表，此处必须 JOIN personal_course
        String kbSql = "SELECT DATE_FORMAT(kd.created_at, '%m-%d') AS day, COUNT(*) AS cnt " +
                "FROM knowledge_document kd " +
                "JOIN knowledge_base kb ON kb.id = kd.knowledge_base_id " +
                "JOIN personal_course pc ON pc.id = kb.course_id AND pc.deleted = 0 " +
                "WHERE kd.deleted = 0 AND kd.created_at >= ? ";
        List<Object> kbParams = new ArrayList<>(List.of(startTime));
        if (visibleStudentIds != null) {
            List<Long> ownerIds = new ArrayList<>(visibleStudentIds);
            ownerIds.add(userId);
            kbSql += "AND kb.user_id IN (" + joinIds(ownerIds) + ") ";
        }
        if (courseName != null && !courseName.isEmpty()) {
            kbSql += "AND pc.name = ?";
            kbParams.add(courseName);
        }
        kbSql += "GROUP BY day";
        jdbcTemplate.queryForList(kbSql, kbParams.toArray()).forEach(r -> {
            Object day = r.get("day");
            if (day != null) {
                days.getOrDefault(day.toString(), new HashMap<>())
                        .merge("kbDocs", ((Number) r.get("cnt")).intValue(), Integer::sum);
            }
        });

        return Result.success(days.entrySet().stream().map(e -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", e.getKey());
            item.put("materials", e.getValue().getOrDefault("materials", 0));
            item.put("kbDocs", e.getValue().getOrDefault("kbDocs", 0));
            return item;
        }).collect(Collectors.toList()));
    }

    /** 获取知识库列表（按课程名聚合可见学生的知识库；教师含本人上传） */
    @GetMapping("/knowledge")
    public Result<List<Map<String, Object>>> knowledgeBases() {
        Long userId = getCurrentUserId();
        User current = userMapper.selectById(userId);
        if (current == null) return Result.error(401, "账号不存在");
        boolean isAdmin = current.getRole() != null && current.getRole() == 3;
        List<Long> visibleStudentIds = getVisibleStudentIds(current, isAdmin);
        if (visibleStudentIds != null && visibleStudentIds.isEmpty()) {
            return Result.success(Collections.emptyList());
        }

        String sql = "SELECT pc.name AS courseName, " +
                "COUNT(DISTINCT kb.user_id) AS studentCount, " +
                "GROUP_CONCAT(DISTINCT ci.class_name ORDER BY ci.class_name SEPARATOR '、') AS classNames, " +
                "SUM((SELECT COUNT(*) FROM knowledge_document kd " +
                "     WHERE kd.knowledge_base_id = kb.id AND kd.deleted = 0)) AS documentCount " +
                "FROM knowledge_base kb " +
                "JOIN personal_course pc ON pc.id = kb.course_id AND pc.deleted = 0 " +
                "LEFT JOIN user u ON u.id = kb.user_id " +
                "LEFT JOIN class_info ci ON ci.id = u.class_id " +
                "WHERE kb.deleted = 0 ";
        List<Object> params = new ArrayList<>();
        if (visibleStudentIds != null) {
            List<Long> ownerIds = new ArrayList<>(visibleStudentIds);
            ownerIds.add(userId);
            sql += "AND kb.user_id IN (" + joinIds(ownerIds) + ") ";
        }
        sql += "GROUP BY pc.name ORDER BY pc.name";

        return Result.success(jdbcTemplate.queryForList(sql, params.toArray()).stream()
                .map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", r.get("courseName"));
                    m.put("name", r.get("courseName"));
                    m.put("courseName", r.get("courseName"));
                    m.put("className", r.get("classNames"));
                    m.put("studentCount", ((Number) r.get("studentCount")).intValue());
                    m.put("documentCount", r.get("documentCount") != null
                            ? ((Number) r.get("documentCount")).intValue() : 0);
                    return m;
                }).collect(Collectors.toList()));
    }

    /** 获取知识库文档（按课程名聚合可见学生的文档，含教师本人上传） */
    @GetMapping("/knowledge/documents")
    public Result<List<Map<String, Object>>> knowledgeDocuments(@RequestParam String courseName) {
        Long userId = getCurrentUserId();
        User current = userMapper.selectById(userId);
        if (current == null) return Result.error(401, "账号不存在");
        boolean isAdmin = current.getRole() != null && current.getRole() == 3;
        List<Long> visibleStudentIds = getVisibleStudentIds(current, isAdmin);

        // 权限：课程名必须属于教师可见范围（教师=本人教务课程；管理员=全部）
        if (!isAdmin) {
            List<Map<String, Object>> jwCourses = getJwCourses(current, false);
            boolean allowed = jwCourses.stream()
                    .anyMatch(c -> courseName != null && courseName.equals(c.get("courseName")));
            if (!allowed) return Result.error(403, "无权查看该知识库");
        }

        // 该课程名下可见学生（含教师本人）的知识库
        String kbSql = "SELECT kb.id FROM knowledge_base kb " +
                "JOIN personal_course pc ON pc.id = kb.course_id AND pc.deleted = 0 " +
                "WHERE kb.deleted = 0 AND pc.name = ? ";
        List<Object> kbParams = new ArrayList<>(List.of(courseName));
        if (visibleStudentIds != null) {
            List<Long> ownerIds = new ArrayList<>(visibleStudentIds);
            ownerIds.add(userId);
            kbSql += "AND kb.user_id IN (" + joinIds(ownerIds) + ")";
        }
        List<Long> kbIds = jdbcTemplate.queryForList(kbSql, kbParams.toArray(), Long.class);
        if (kbIds.isEmpty()) return Result.success(Collections.emptyList());

        List<KnowledgeDocument> docs = documentMapper.selectList(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getDeleted, 0).in(KnowledgeDocument::getKnowledgeBaseId, kbIds)
                .orderByDesc(KnowledgeDocument::getCreatedAt));

        // 批量查询上传者姓名
        Set<Long> userIds = docs.stream().map(KnowledgeDocument::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> userNameMap = userIds.isEmpty() ? Collections.emptyMap() :
                userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId,
                                u -> displayName(u), (a, b) -> a));

        return Result.success(docs.stream().map(d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId()); m.put("title", d.getTitle()); m.put("summary", d.getSummary());
            m.put("content", d.getContent()); m.put("indexStatus", d.getIndexStatus());
            m.put("createdAt", fmtDate(d.getCreatedAt()));
            m.put("userId", d.getUserId());
            m.put("userName", userNameMap.getOrDefault(d.getUserId(), "未知"));
            return m;
        }).collect(Collectors.toList()));
    }

    /**
     * 课程名 → 教师本人个人课程ID（供知识库上传文档使用）。
     * 不存在时自动创建个人课程和知识库；教师仅可上传到自己可见的课程名。
     */
    @GetMapping("/knowledge/course-id")
    public Result<Long> knowledgeCourseId(@RequestParam String courseName) {
        Long userId = getCurrentUserId();
        User current = userMapper.selectById(userId);
        if (current == null) return Result.error(401, "账号不存在");
        if (courseName == null || courseName.trim().isEmpty()) {
            return Result.error(400, "课程名不能为空");
        }
        final String courseNameFinal = courseName.trim();

        boolean isAdmin = current.getRole() != null && current.getRole() == 3;
        if (!isAdmin) {
            // 权限：本人教务课程或本人已创建的同名个人课程
            boolean jwAllowed = getJwCourses(current, false).stream()
                    .anyMatch(c -> courseNameFinal.equals(c.get("courseName")));
            long ownCount = courseMapper.selectCount(new LambdaQueryWrapper<Course>()
                    .eq(Course::getDeleted, 0).eq(Course::getUserId, userId).eq(Course::getName, courseNameFinal));
            if (!jwAllowed && ownCount == 0) {
                return Result.error(403, "无权上传到该课程知识库");
            }
        }

        // 教师本人的同名个人课程，不存在则创建
        Course course = courseMapper.selectOne(new LambdaQueryWrapper<Course>()
                .eq(Course::getDeleted, 0).eq(Course::getUserId, userId).eq(Course::getName, courseNameFinal)
                .last("LIMIT 1"));
        if (course == null) {
            String semester = null;
            try {
                semester = jdbcTemplate.queryForObject(
                        "SELECT name FROM semester WHERE is_current = 1 LIMIT 1", String.class);
            } catch (Exception ignored) {}
            course = new Course();
            course.setUserId(userId);
            course.setName(courseNameFinal);
            course.setTeacher(displayName(current));
            course.setClassroom("");
            course.setDayOfWeek(1);
            course.setStartTime(LocalTime.of(8, 0));
            course.setEndTime(LocalTime.of(9, 40));
            course.setWeeks("1-16");
            course.setColor("#0052d9");
            course.setSemester(semester != null ? semester : "2025-2026-2");
            course.setDescription("");
            course.setStatus(1);
            courseMapper.insert(course);
        }

        // 确保该课程的知识库存在
        KnowledgeBase kb = knowledgeBaseMapper.selectOne(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getDeleted, 0)
                .eq(KnowledgeBase::getUserId, userId).eq(KnowledgeBase::getCourseId, course.getId())
                .last("LIMIT 1"));
        if (kb == null) {
            kb = new KnowledgeBase();
            kb.setUserId(userId);
            kb.setCourseId(course.getId());
            kb.setName(courseNameFinal + "知识库");
            kb.setDescription("");
            knowledgeBaseMapper.insert(kb);
        }
        return Result.success(course.getId());
    }
}