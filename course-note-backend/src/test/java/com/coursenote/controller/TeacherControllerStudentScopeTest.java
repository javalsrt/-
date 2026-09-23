package com.coursenote.controller;

import com.coursenote.common.Result;
import com.coursenote.config.JwtUtil;
import com.coursenote.entity.AiChatRecord;
import com.coursenote.entity.Course;
import com.coursenote.entity.KnowledgeBase;
import com.coursenote.entity.KnowledgeDocument;
import com.coursenote.entity.Material;
import com.coursenote.entity.User;
import com.coursenote.mapper.AiChatRecordMapper;
import com.coursenote.mapper.CourseMapper;
import com.coursenote.mapper.CourseSessionMapper;
import com.coursenote.mapper.KnowledgeBaseMapper;
import com.coursenote.mapper.KnowledgeDocumentMapper;
import com.coursenote.mapper.MaterialMapper;
import com.coursenote.mapper.UserMapper;
import com.znxsgl.entity.ClassInfo;
import com.znxsgl.entity.Teacher;
import com.znxsgl.mapper.ClassInfoMapper;
import com.znxsgl.mapper.TeacherMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeacherControllerStudentScopeTest {

    @Mock private JwtUtil jwtUtil;
    @Mock private CourseMapper courseMapper;
    @Mock private CourseSessionMapper sessionMapper;
    @Mock private MaterialMapper materialMapper;
    @Mock private UserMapper userMapper;
    @Mock private TeacherMapper teacherMapper;
    @Mock private KnowledgeBaseMapper knowledgeBaseMapper;
    @Mock private KnowledgeDocumentMapper documentMapper;
    @Mock private AiChatRecordMapper chatRecordMapper;
    @Mock private ClassInfoMapper classInfoMapper;
    @Mock private JdbcTemplate jdbcTemplate;

    private TeacherController controller;

    @BeforeEach
    void setUp() {
        controller = new TeacherController();
        ReflectionTestUtils.setField(controller, "jwtUtil", jwtUtil);
        ReflectionTestUtils.setField(controller, "courseMapper", courseMapper);
        ReflectionTestUtils.setField(controller, "sessionMapper", sessionMapper);
        ReflectionTestUtils.setField(controller, "materialMapper", materialMapper);
        ReflectionTestUtils.setField(controller, "userMapper", userMapper);
        ReflectionTestUtils.setField(controller, "teacherMapper", teacherMapper);
        ReflectionTestUtils.setField(controller, "knowledgeBaseMapper", knowledgeBaseMapper);
        ReflectionTestUtils.setField(controller, "documentMapper", documentMapper);
        ReflectionTestUtils.setField(controller, "chatRecordMapper", chatRecordMapper);
        ReflectionTestUtils.setField(controller, "classInfoMapper", classInfoMapper);
        ReflectionTestUtils.setField(controller, "jdbcTemplate", jdbcTemplate);
        when(jwtUtil.validateToken(anyString())).thenReturn(true);
    }

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void teacherStudentsReturnsOnlyStudentsFromTaughtClasses() {
        User teacher = user(200L, 2, "张伟", null);
        User taughtStudent = user(101L, 1, "学生甲", 10L);
        User otherStudent = user(102L, 1, "学生乙", 20L);
        Teacher teacherProfile = new Teacher();
        teacherProfile.setId(900L);
        teacherProfile.setRealName("张伟");

        authenticateAs(200L);
        when(userMapper.selectById(200L)).thenReturn(teacher);
        when(teacherMapper.selectOne(any())).thenReturn(teacherProfile);
        when(jdbcTemplate.queryForList(anyString(), anyLong())).thenReturn(List.of(Map.of("classId", 10L)));
        when(userMapper.selectList(any())).thenReturn(List.of(taughtStudent));
        when(classInfoMapper.selectList(null)).thenReturn(List.of(classInfo(10L, "软件2301班")));
        when(materialMapper.countByUserIds(anyList())).thenReturn(List.of());
        when(documentMapper.countByUserIds(anyList())).thenReturn(List.of());

        Result<List<Map<String, Object>>> result = controller.students();

        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().size());
        assertEquals(101L, result.getData().get(0).get("id"));
        assertEquals("软件2301班", result.getData().get(0).get("className"));
    }

    @Test
    void teacherCannotReadStudentOutsideTaughtClasses() {
        User teacher = user(200L, 2, "张伟", null);
        User otherStudent = user(102L, 1, "学生乙", 20L);
        Teacher teacherProfile = new Teacher();
        teacherProfile.setId(900L);
        teacherProfile.setRealName("张伟");

        authenticateAs(200L);
        when(userMapper.selectById(200L)).thenReturn(teacher);
        when(userMapper.selectById(102L)).thenReturn(otherStudent);
        when(teacherMapper.selectOne(any())).thenReturn(teacherProfile);
        when(jdbcTemplate.queryForList(anyString(), anyLong())).thenReturn(List.of(Map.of("classId", 10L)));

        Result<Map<String, Object>> result = controller.studentDetail(102L);

        assertEquals(403, result.getCode());
        assertNull(result.getData());
    }

    @Test
    void adminStudentsReturnsAllStudentsWithoutClassFilter() {
        User admin = user(1L, 3, "管理员", null);
        User firstStudent = user(101L, 1, "学生甲", 10L);
        User secondStudent = user(102L, 1, "学生乙", 20L);

        authenticateAs(1L);
        when(userMapper.selectById(1L)).thenReturn(admin);
        when(userMapper.selectList(any())).thenReturn(List.of(firstStudent, secondStudent));
        when(classInfoMapper.selectList(null)).thenReturn(List.of(
                classInfo(10L, "软件2301班"), classInfo(20L, "软件2302班")));
        when(materialMapper.countByUserIds(anyList())).thenReturn(List.of());
        when(documentMapper.countByUserIds(anyList())).thenReturn(List.of());

        Result<List<Map<String, Object>>> result = controller.students();

        assertEquals(200, result.getCode());
        assertEquals(2, result.getData().size());
        assertEquals(101L, result.getData().get(0).get("id"));
        assertEquals(102L, result.getData().get(1).get("id"));
    }

    @Test
    void adminCanReadAnyStudentDetail() {
        User admin = user(1L, 3, "管理员", null);
        User student = user(102L, 1, "学生乙", 20L);

        authenticateAs(1L);
        when(userMapper.selectById(1L)).thenReturn(admin);
        when(userMapper.selectById(102L)).thenReturn(student);
        when(classInfoMapper.selectById(20L)).thenReturn(classInfo(20L, "软件2302班"));
        when(materialMapper.selectCount(any())).thenReturn(0L);
        when(chatRecordMapper.selectCount(any())).thenReturn(0L);
        when(documentMapper.selectCount(any())).thenReturn(0L);
        when(knowledgeBaseMapper.findByUserId(102L)).thenReturn(List.of());
        when(materialMapper.selectList(any())).thenReturn(List.of());
        when(documentMapper.selectList(any())).thenReturn(List.of());
        when(materialMapper.countByUserIdAndDate(anyLong(), anyString(), anyString())).thenReturn(List.of());
        when(documentMapper.countByUserIdAndDate(anyLong(), anyString(), anyString())).thenReturn(List.of());
        when(chatRecordMapper.countByUserIdAndDate(anyLong(), anyString(), anyString())).thenReturn(List.of());

        Result<Map<String, Object>> result = controller.studentDetail(102L);

        assertEquals(200, result.getCode());
        assertEquals(102L, result.getData().get("id"));
        assertEquals("软件2302班", result.getData().get("className"));
    }

    private void authenticateAs(Long userId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer scope-test-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(jwtUtil.getUserIdFromToken("scope-test-token")).thenReturn(userId);
    }

    private User user(Long id, Integer role, String realName, Long classId) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setRealName(realName);
        user.setNickname(realName);
        user.setClassId(classId);
        user.setDeleted(0);
        user.setStatus(1);
        user.setStudentNo(String.valueOf(id));
        return user;
    }

    private ClassInfo classInfo(Long id, String name) {
        ClassInfo classInfo = new ClassInfo();
        classInfo.setId(id);
        classInfo.setClassName(name);
        return classInfo;
    }
}
