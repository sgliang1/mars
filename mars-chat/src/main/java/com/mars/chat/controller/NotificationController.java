package com.mars.chat.controller;

import com.mars.chat.service.NotificationService;
import com.mars.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/mars-chat/notifications")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @GetMapping
    public Result<List<Map<String, Object>>> list(@RequestHeader("X-User-Id") String userIdStr) {
        try {
            return Result.success(notificationService.listNotifications(parseUserId(userIdStr)));
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("Failed to load notifications");
        }
    }

    @PostMapping("/{id}/read")
    public Result<Map<String, Object>> markRead(@PathVariable Long id,
                                                @RequestHeader("X-User-Id") String userIdStr) {
        try {
            return Result.success(notificationService.markRead(parseUserId(userIdStr), id));
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("Failed to update notification");
        }
    }

    @PostMapping("/read-all")
    public Result<Map<String, Object>> markAllRead(@RequestHeader("X-User-Id") String userIdStr) {
        try {
            int count = notificationService.markAllRead(parseUserId(userIdStr));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("updatedCount", count);
            return Result.success(data);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("Failed to mark notifications read");
        }
    }

    private Long parseUserId(String userIdStr) {
        return Long.parseLong(userIdStr);
    }
}
