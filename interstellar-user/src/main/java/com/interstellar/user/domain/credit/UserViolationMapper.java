package com.interstellar.user.domain.credit;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interstellar.user.domain.credit.UserViolation;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserViolationMapper extends BaseMapper<UserViolation> {
}