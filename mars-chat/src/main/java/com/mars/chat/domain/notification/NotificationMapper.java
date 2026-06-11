package com.mars.chat.domain.notification;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mars.chat.domain.notification.Notification;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
}
