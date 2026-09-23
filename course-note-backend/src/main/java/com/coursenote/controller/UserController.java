package com.coursenote.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.coursenote.common.Result;
import com.coursenote.entity.Course;
import com.coursenote.entity.KnowledgeBase;
import com.coursenote.entity.Material;
import com.coursenote.entity.User;
import com.coursenote.mapper.CourseMapper;
import com.coursenote.mapper.KnowledgeBaseMapper;
import com.coursenote.mapper.MaterialMapper;
import com.coursenote.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Autowired
    private MaterialMapper materialMapper;

    /** 获取用户信息 */
    @GetMapping("/info")
    public Result<User> getUserInfo(@RequestAttribute Long userId) {
        User user = userService.getById(userId);
        // 教务账号昵称为空时，用真实姓名兜底展示
        if (user != null && (user.getNickname() == null || user.getNickname().trim().isEmpty())
                && user.getRealName() != null && !user.getRealName().trim().isEmpty()) {
            user.setNickname(user.getRealName());
        }
        return Result.success(user);
    }

    /** 更新用户信息 */
    @PutMapping("/info")
    public Result<User> updateUserInfo(@RequestAttribute Long userId, @RequestBody User user) {
        user.setId(userId);
        return Result.success(userService.updateUser(user));
    }

    /**
     * 获取用户统计数据（课程数、知识库数、笔记数）
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats(@RequestAttribute Long userId) {
        Map<String, Object> stats = new HashMap<>();

        // 课程数：统计该用户个人课表中的有效课程
        Long courseCount = courseMapper.selectCount(
                new LambdaQueryWrapper<Course>()
                        .eq(Course::getUserId, userId)
                        .eq(Course::getStatus, 1));
        stats.put("courseCount", courseCount != null ? courseCount : 0);

        // 知识库数：用户创建的知识库数量
        Long kbCount = knowledgeBaseMapper.selectCount(
                new LambdaQueryWrapper<KnowledgeBase>()
                        .eq(KnowledgeBase::getUserId, userId));
        stats.put("knowledgeCount", kbCount != null ? kbCount : 0);

        Long noteCount = materialMapper.selectCount(
                new LambdaQueryWrapper<Material>()
                        .eq(Material::getUserId, userId)
                        .eq(Material::getType, "NOTE")
                        .eq(Material::getDeleted, 0));
        stats.put("noteCount", noteCount != null ? noteCount : 0);

        return Result.success(stats);
    }
}
