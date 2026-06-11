package com.mars.auth.domain.account;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mars.auth.domain.account.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper // 关键注解，交�?Spring 管理
public interface UserMapper extends BaseMapper<User> {
    // MP 自动帮你写好�?CRUD，这里什么都不用�?
}
