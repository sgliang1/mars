package com.mars.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mars.auth.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper // 关键注解，交给 Spring 管理
public interface UserMapper extends BaseMapper<User> {
    // MP 自动帮你写好了 CRUD，这里什么都不用写
}