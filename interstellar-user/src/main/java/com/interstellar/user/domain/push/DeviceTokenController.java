package com.interstellar.user.domain.push;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interstellar.common.Result;
import com.interstellar.common.push.DeviceToken;
import com.interstellar.common.push.DeviceTokenMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/device-token")
@Tag(name = "设备令牌", description = "推送设备令牌注册与注销")
public class DeviceTokenController {

    @Autowired
    private DeviceTokenMapper deviceTokenMapper;

    @PostMapping
    @Operation(summary = "注册/更新设备令牌")
    public Result<String> register(@RequestHeader("X-User-Id") String userIdStr,
                                   @RequestBody Map<String, String> body) {
        Long userId = Long.parseLong(userIdStr);
        String token = body.get("token");
        String platform = body.get("platform");
        String deviceId = body.get("deviceId");

        if (token == null || token.isBlank()) {
            return Result.fail("令牌不能为空");
        }

        // 按 userId + deviceId 去重（同一设备只保留最新令牌）
        DeviceToken existing = deviceTokenMapper.selectOne(
                new LambdaQueryWrapper<DeviceToken>()
                        .eq(DeviceToken::getUserId, userId)
                        .eq(DeviceToken::getDeviceId, deviceId)
                        .last("limit 1"));

        if (existing != null) {
            existing.setToken(token);
            existing.setPlatform(platform);
            existing.setUpdatedAt(LocalDateTime.now());
            deviceTokenMapper.updateById(existing);
        } else {
            DeviceToken dt = new DeviceToken();
            dt.setUserId(userId);
            dt.setToken(token);
            dt.setPlatform(platform);
            dt.setDeviceId(deviceId);
            dt.setCreatedAt(LocalDateTime.now());
            dt.setUpdatedAt(LocalDateTime.now());
            deviceTokenMapper.insert(dt);
        }
        return Result.successMessage("注册成功");
    }

    @DeleteMapping
    @Operation(summary = "注销设备令牌")
    public Result<String> remove(@RequestHeader("X-User-Id") String userIdStr,
                                 @RequestParam("deviceId") String deviceId) {
        Long userId = Long.parseLong(userIdStr);
        deviceTokenMapper.delete(
                new LambdaQueryWrapper<DeviceToken>()
                        .eq(DeviceToken::getUserId, userId)
                        .eq(DeviceToken::getDeviceId, deviceId));
        return Result.successMessage("注销成功");
    }
}