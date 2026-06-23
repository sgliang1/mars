package com.mars.user.domain.notification;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mars.common.model.Notification;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
}