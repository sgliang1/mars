package com.interstellar.user.domain.push;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interstellar.common.Result;
import com.interstellar.common.push.NotificationPreference;
import com.interstellar.common.push.NotificationPreferenceMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

@RestController
@RequestMapping("/notification-preference")
@Tag(name = "通知偏好", description = "用户通知推送偏好设置")
public class NotificationPreferenceController {

    @Autowired
    private NotificationPreferenceMapper preferenceMapper;

    @GetMapping
    @Operation(summary = "获取通知偏好")
    public Result<NotificationPreference> get(@RequestHeader("X-User-Id") String userIdStr) {
        Long userId = Long.parseLong(userIdStr);
        NotificationPreference pref = preferenceMapper.selectById(userId);
        if (pref == null) {
            pref = NotificationPreference.defaults(userId);
        }
        return Result.success(pref);
    }

    @PutMapping
    @Operation(summary = "更新通知偏好")
    public Result<String> update(@RequestHeader("X-User-Id") String userIdStr,
                                 @RequestBody Map<String, Object> body) {
        Long userId = Long.parseLong(userIdStr);
        NotificationPreference pref = preferenceMapper.selectById(userId);
        if (pref == null) {
            pref = NotificationPreference.defaults(userId);
            pref.setUserId(userId);
            applyFields(pref, body);
            pref.setUpdatedAt(LocalDateTime.now());
            preferenceMapper.insert(pref);
        } else {
            applyFields(pref, body);
            pref.setUpdatedAt(LocalDateTime.now());
            preferenceMapper.updateById(pref);
        }
        return Result.successMessage("保存成功");
    }

    private void applyFields(NotificationPreference pref, Map<String, Object> body) {
        if (body.containsKey("quietEnabled"))
            pref.setQuietEnabled((Boolean) body.get("quietEnabled"));
        if (body.containsKey("quietStart"))
            pref.setQuietStart(LocalTime.parse((String) body.get("quietStart")));
        if (body.containsKey("quietEnd"))
            pref.setQuietEnd(LocalTime.parse((String) body.get("quietEnd")));
        if (body.containsKey("interactionEnabled"))
            pref.setInteractionEnabled((Boolean) body.get("interactionEnabled"));
        if (body.containsKey("chatPushEnabled"))
            pref.setChatPushEnabled((Boolean) body.get("chatPushEnabled"));
        if (body.containsKey("systemEnabled"))
            pref.setSystemEnabled((Boolean) body.get("systemEnabled"));
    }
}