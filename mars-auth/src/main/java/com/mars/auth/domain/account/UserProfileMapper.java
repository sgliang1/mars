package com.mars.auth.domain.account;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mars.auth.domain.account.UserProfile;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserProfileMapper extends BaseMapper<UserProfile> {
}
