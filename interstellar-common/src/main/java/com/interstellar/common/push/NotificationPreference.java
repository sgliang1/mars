package com.interstellar.common.push;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 通知偏好实体 (共享表)
 */
@Data
@TableName("notification_preference")
public class NotificationPreference {
    @TableId
    @TableField("user_id")
    private Long userId;
    @TableField("quiet_enabled")
    private Boolean quietEnabled;
    @TableField("quiet_start")
    private LocalTime quietStart;
    @TableField("quiet_end")
    private LocalTime quietEnd;
    @TableField("interaction_enabled")
    private Boolean interactionEnabled;
    @TableField("chat_push_enabled")
    private Boolean chatPushEnabled;
    @TableField("system_enabled")
    private Boolean systemEnabled;
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public static NotificationPreference defaults(Long userId) {
        NotificationPreference pref = new NotificationPreference();
        pref.setUserId(userId);
        pref.setQuietEnabled(false);
        pref.setQuietStart(LocalTime.of(22, 0));
        pref.setQuietEnd(LocalTime.of(8, 0));
        pref.setInteractionEnabled(true);
        pref.setChatPushEnabled(true);
        pref.setSystemEnabled(true);
        return pref;
    }

    public boolean isInQuietHours() {
        if (!Boolean.TRUE.equals(quietEnabled)) return false;
        LocalTime now = LocalTime.now();
        if (quietStart.isBefore(quietEnd)) {
            return !now.isBefore(quietStart) && !now.isAfter(quietEnd);
        } else {
            return !now.isBefore(quietStart) || !now.isAfter(quietEnd);
        }
    }
}