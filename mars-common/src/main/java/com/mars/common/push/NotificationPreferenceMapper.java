package com.mars.common.push;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通知偏好 Mapper (共享表, 供各服务查询推送偏好)
 */
@Mapper
public interface NotificationPreferenceMapper extends BaseMapper<NotificationPreference> {
}