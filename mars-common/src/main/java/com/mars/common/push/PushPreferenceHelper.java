package com.mars.common.push;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

/**
 * 推送偏好检查工具
 * 供各 MQ Consumer 查询用户的推送偏好，决定是否实际推送
 */
@Component
@ConditionalOnClass(name = "com.baomidou.mybatisplus.core.mapper.BaseMapper")
public class PushPreferenceHelper {

    @Autowired
    private NotificationPreferenceMapper preferenceMapper;

    /**
     * 获取用户通知偏好，未设置时返回默认值
     */
    public NotificationPreference getPreference(Long userId) {
        NotificationPreference pref = preferenceMapper.selectById(userId);
        return pref != null ? pref : NotificationPreference.defaults(userId);
    }

    /**
     * 判断互动通知是否应该推送
     */
    public boolean shouldPushInteraction(Long userId) {
        NotificationPreference pref = getPreference(userId);
        return Boolean.TRUE.equals(pref.getInteractionEnabled()) && !pref.isInQuietHours();
    }

    /**
     * 判断聊天通知是否应该推送
     */
    public boolean shouldPushChat(Long userId) {
        NotificationPreference pref = getPreference(userId);
        return Boolean.TRUE.equals(pref.getChatPushEnabled()) && !pref.isInQuietHours();
    }
}