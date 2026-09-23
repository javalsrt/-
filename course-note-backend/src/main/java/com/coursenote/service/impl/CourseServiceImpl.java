package com.coursenote.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.coursenote.common.BusinessException;
import com.coursenote.dto.CourseDTO;
import com.coursenote.entity.Course;
import com.coursenote.entity.User;
import com.coursenote.mapper.CourseMapper;
import com.coursenote.mapper.UserMapper;
import com.coursenote.service.CourseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CourseServiceImpl implements CourseService {

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Override
    public Map<Integer, List<Course>> getCourseSchedule(Long userId, String semester) {
        User user = userMapper.selectById(userId);
        String grade = (user != null && user.getGrade() != null) ? user.getGrade().trim() : "";

        // 班级制学生优先：以本人"影子课程"为准，并与班级课表增量同步（新导入课程自动出现）。
        List<Course> courses = syncClassScheduleCourses(userId, user);

        // 无班级课表：回退旧的年级共享课程体系（手建课程等场景）
        if (courses.isEmpty()) {
            courses = tryMatchCourses(grade, semester);
        }
        if (courses.isEmpty() && !grade.isEmpty()) {
            courses = tryLikeCourses(grade, semester);
        }
        // LIKE也查不到时：用年级末两位数字（如"2022级" → "22"）再试
        if (courses.isEmpty() && !grade.isEmpty()) {
            String shortYear = grade.replaceAll("[^0-9]", "");
            if (shortYear.length() >= 2) {
                shortYear = shortYear.substring(shortYear.length() - 2);
                courses = tryLikeCourses(shortYear, semester);
            }
        }

        if (courses.isEmpty()) {
            log.info("用户{} (年级:{}) 无课程数据", userId, grade);
            return new LinkedHashMap<>();
        }

        // 提取description去重：如果查到了多个班级的课，只保留第一个班级的课程
        Set<String> descSet = new LinkedHashSet<>();
        for (Course c : courses) {
            if (c.getDescription() != null) descSet.add(c.getDescription());
        }
        if (descSet.size() > 1) {
            String primaryDesc = descSet.iterator().next();
            String finalPrimaryDesc = primaryDesc;
            courses = courses.stream()
                    .filter(c -> finalPrimaryDesc.equals(c.getDescription()))
                    .collect(Collectors.toList());
            log.info("年级:{} 匹配到多个班级，锁定班级:{} (共{}个班级)", grade, primaryDesc, descSet.size());
        }

        return courses.stream()
                .filter(c -> c.getDayOfWeek() != null && c.getDayOfWeek() >= 1 && c.getDayOfWeek() <= 7)
                .collect(Collectors.groupingBy(Course::getDayOfWeek, LinkedHashMap::new, Collectors.toList()));
    }

    private List<Course> tryMatchCourses(String description, String semester) {
        LambdaQueryWrapper<Course> w = new LambdaQueryWrapper<Course>()
                .eq(Course::getDeleted, 0).eq(Course::getStatus, 1)
                .eq(Course::getDescription, description);
        if (semester != null && !semester.isEmpty()) w.eq(Course::getSemester, semester);
        w.orderByAsc(Course::getDayOfWeek, Course::getStartTime);
        return courseMapper.selectList(w);
    }

    private List<Course> tryLikeCourses(String year, String semester) {
        LambdaQueryWrapper<Course> w = new LambdaQueryWrapper<Course>()
                .eq(Course::getDeleted, 0).eq(Course::getStatus, 1)
                .like(Course::getDescription, year);
        if (semester != null && !semester.isEmpty()) w.eq(Course::getSemester, semester);
        w.orderByAsc(Course::getDayOfWeek, Course::getStartTime);
        return courseMapper.selectList(w);
    }

    /**
     * 班级制教务同步：将学生所在班级的课表课程同步为本人"影子课程"。
     *
     * 语义（增量同步）：
     *   - 班级课表中的每门课，若本人无同名课程则创建影子课程（userId=本人）
     *   - 班级课表新增课程（如管理员后续导入）会在下次请求时自动出现
     *   - 班级无课表时返回本人已有影子课程（历史同步结果），不返回空以避免误入年级共享体系
     *   - 已删除的课程不清理（保护知识库/文档关联）
     *
     * description 存 'class:{classId}'（班级标识），与年级共享课程的 description=年级
     * 严格隔离，防止影子课程被其他学生按年级匹配到（串号）。
     */
    private List<Course> syncClassScheduleCourses(Long userId, User user) {
        if (user == null || user.getClassId() == null) {
            return Collections.emptyList();
        }
        Long classId = user.getClassId();

        // 本人现有影子课程（含历史创建的 description='年级' 旧记录）
        List<Course> ownCourses = courseMapper.selectList(new LambdaQueryWrapper<Course>()
                .eq(Course::getUserId, userId)
                .eq(Course::getDeleted, 0)
                .orderByAsc(Course::getDayOfWeek, Course::getStartTime));
        Map<String, Course> ownByName = new LinkedHashMap<>();
        for (Course c : ownCourses) {
            ownByName.put(c.getName(), c);
        }

        // 班级课表课程（去重）
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT s.course_name AS courseName, " +
            "       MIN(s.day_of_week) AS dayOfWeek, " +
            "       MIN(s.start_time) AS startTime, " +
            "       MIN(s.end_time) AS endTime, " +
            "       MIN(s.classroom) AS classroom, " +
            "       MAX(t.real_name) AS teacherName, " +
            "       MAX(s.semester) AS semester " +
            "FROM schedule s " +
            "LEFT JOIN course c ON c.id = s.course_id " +
            "LEFT JOIN teacher t ON t.id = c.teacher_id " +
            "WHERE s.user_id = ? AND s.status = 1 " +
            "GROUP BY s.course_name " +
            "ORDER BY MIN(s.day_of_week), MIN(s.start_time)", userId);

        if (rows.isEmpty()) {
            // 班级课表为空：返回已有影子课程（历史同步），无则空（外层走年级共享回退）
            log.debug("用户{} (班级:{}) 班级课表无课程", userId, classId);
            return ownCourses;
        }

        String classDesc = "class:" + classId;
        List<Course> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String name = (String) row.get("courseName");
            if (name == null || name.isEmpty()) continue;

            Course existing = ownByName.get(name);
            if (existing != null) {
                // 旧版影子课程 description=年级 → 更新为班级标识，隔离串号风险
                if (!classDesc.equals(existing.getDescription())) {
                    existing.setDescription(classDesc);
                    courseMapper.updateById(existing);
                }
                result.add(existing);
                continue;
            }

            // 创建影子课程
            Course c = new Course();
            c.setUserId(userId);
            c.setName(name);
            c.setTeacher((String) row.get("teacherName"));
            c.setClassroom((String) row.get("classroom"));
            Object dow = row.get("dayOfWeek");
            c.setDayOfWeek(dow != null ? ((Number) dow).intValue() : 1);
            c.setStartTime(toLocalTime(row.get("startTime"), LocalTime.of(8, 10)));
            c.setEndTime(toLocalTime(row.get("endTime"), LocalTime.of(9, 40)));
            c.setWeeks("[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16]");
            c.setSemester((String) row.get("semester"));
            c.setDescription(classDesc);
            c.setStatus(1);
            courseMapper.insert(c);
            result.add(c);
            log.info("班级课表同步影子课程: userId={}, classId={}, course={} (id={})", userId, classId, name, c.getId());
        }
        return result;
    }

    /** 数据库时间值（java.sql.Time / LocalTime / String）转 LocalTime，失败返回默认值 */
    private LocalTime toLocalTime(Object value, LocalTime defaultValue) {
        if (value == null) return defaultValue;
        try {
            if (value instanceof LocalTime) return (LocalTime) value;
            String s = value.toString();
            return LocalTime.parse(s.length() > 5 ? s.substring(0, 5) : s);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    @Override
    public Course getById(Long id, Long userId) {
        Course course = courseMapper.selectById(id);
        if (course == null || course.getDeleted() == 1) {
            throw new BusinessException("课程不存在");
        }
        // 开发模式：放宽权限校验，所有用户可访问任意课程
        return course;
    }

    @Override
    public Course createCourse(Long userId, CourseDTO dto) {
        Course course = new Course();
        course.setUserId(userId);
        course.setName(dto.getName());
        course.setTeacher(dto.getTeacher());
        course.setClassroom(dto.getClassroom());
        course.setDayOfWeek(dto.getDayOfWeek());
        course.setStartTime(LocalTime.parse(dto.getStartTime()));
        course.setEndTime(LocalTime.parse(dto.getEndTime()));
        course.setWeeks(dto.getWeeks());
        course.setColor(normalizeColor(dto.getColor() != null ? dto.getColor() : "#4A90D9"));
        course.setSemester(dto.getSemester());
        course.setDescription(dto.getDescription());
        course.setStatus(1);

        courseMapper.insert(course);
        return course;
    }

    @Override
    public Course updateCourse(Long id, Long userId, CourseDTO dto) {
        Course course = getById(id, userId);

        if (dto.getName() != null) course.setName(dto.getName());
        if (dto.getTeacher() != null) course.setTeacher(dto.getTeacher());
        if (dto.getClassroom() != null) course.setClassroom(dto.getClassroom());
        if (dto.getDayOfWeek() != null) course.setDayOfWeek(dto.getDayOfWeek());
        if (dto.getStartTime() != null) course.setStartTime(LocalTime.parse(dto.getStartTime()));
        if (dto.getEndTime() != null) course.setEndTime(LocalTime.parse(dto.getEndTime()));
        if (dto.getWeeks() != null) course.setWeeks(dto.getWeeks());
        if (dto.getColor() != null) course.setColor(normalizeColor(dto.getColor()));
        if (dto.getSemester() != null) course.setSemester(dto.getSemester());
        if (dto.getDescription() != null) course.setDescription(dto.getDescription());

        courseMapper.updateById(course);
        return courseMapper.selectById(id);
    }

    @Override
    public void deleteCourse(Long id, Long userId) {
        Course course = getById(id, userId);
        courseMapper.deleteById(id);
    }

    private String normalizeColor(String color) {
        if (color == null) return color;
        color = color.trim();
        if (color.length() > 64) color = color.substring(0, 64);
        if (color.matches("^#[0-9a-fA-F]{6}$")) return color;
        if (color.matches("^#[0-9a-fA-F]{3}$")) {
            return "#" + color.charAt(1) + color.charAt(1)
                    + color.charAt(2) + color.charAt(2)
                    + color.charAt(3) + color.charAt(3);
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("^rgba?\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*(?:,\\s*[\\d.]+\\s*)?\\)$")
            .matcher(color);
        if (m.find()) {
            return String.format("#%02x%02x%02x",
                    Integer.parseInt(m.group(1)),
                    Integer.parseInt(m.group(2)),
                    Integer.parseInt(m.group(3)));
        }
        return color;
    }
}
