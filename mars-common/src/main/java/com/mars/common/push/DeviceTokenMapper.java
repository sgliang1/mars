package com.mars.common.push;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 设备令牌 Mapper (共享表, 供 PushService 查询)
 */
@Mapper
public interface DeviceTokenMapper extends BaseMapper<DeviceToken> {

    @Select("SELECT * FROM device_token WHERE user_id = #{userId}")
    List<DeviceToken> selectByUserId(@Param("userId") Long userId);
}