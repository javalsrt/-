package com.coursenote.service;

import com.coursenote.entity.User;

public interface UserService {
    /**
     * 根据ID获取用户
     */
    User getById(Long id);

    /**
     * 更新用户信息
     */
    User updateUser(User user);
}
