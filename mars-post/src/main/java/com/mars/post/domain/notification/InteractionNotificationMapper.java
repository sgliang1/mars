package com.mars.post.domain.notification;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mars.common.model.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface InteractionNotificationMapper extends BaseMapper<Notification> {

    @Update("UPDATE notification SET read_status = 1, read_at = NOW() WHERE user_id = #{userId} AND category = 'interaction' AND read_status = 0")
    int markAllInteractionReadByUserId(Long userId);
}