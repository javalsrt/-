package com.coursenote.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.coursenote.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
