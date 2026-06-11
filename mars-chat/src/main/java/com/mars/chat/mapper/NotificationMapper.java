package com.mars.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mars.chat.entity.Notification;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
}
