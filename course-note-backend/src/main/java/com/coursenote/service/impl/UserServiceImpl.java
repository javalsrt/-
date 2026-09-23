package com.coursenote.service.impl;

import com.coursenote.entity.User;
import com.coursenote.mapper.UserMapper;
import com.coursenote.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Override
    public User getById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null || user.getDeleted() == 1) {
            // 开发模式：返回一个虚拟用户
            log.warn("用户不存在 id={}，返回虚拟用户", id);
            User demo = new User();
            demo.setId(id);
            demo.setOpenid("dev_demo");
            demo.setNickname("演示用户");
            demo.setSchool("示例大学");
            demo.setMajor("计算机科学");
            return demo;
        }
        return user;
    }

    @Override
    public User updateUser(User user) {
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return userMapper.selectById(user.getId());
    }
}
