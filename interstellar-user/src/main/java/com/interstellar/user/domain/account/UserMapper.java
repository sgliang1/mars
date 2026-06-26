package com.interstellar.user.domain.account;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interstellar.common.model.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}