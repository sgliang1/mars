package com.interstellar.chat.domain.notification;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interstellar.common.model.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    @Update("UPDATE notification SET read_status = 1, read_at = NOW() WHERE user_id = #{userId} AND read_status = 0")
    int markAllReadByUserId(Long userId);
}