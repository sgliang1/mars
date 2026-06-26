package com.interstellar.chat.domain.notification;

import com.interstellar.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/notifications")
@Tag(name = "系统通知", description = "系统/安全/版本通知")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @GetMapping
    @Operation(summary = "系统通知列表")
    public Result<List<Map<String, Object>>> list(@RequestHeader("X-User-Id") String userIdStr,
                                                  @RequestParam(value = "page", defaultValue = "1") int page,
                                                  @RequestParam(value = "size", defaultValue = "50") int size) {
        return Result.success(notificationService.listNotifications(Long.parseLong(userIdStr)));
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "标记通知已读")
    public Result<Map<String, Object>> markRead(@Parameter(description = "通知ID") @PathVariable("id") Long id,
                                                @RequestHeader("X-User-Id") String userIdStr) {
        return Result.success(notificationService.markRead(Long.parseLong(userIdStr), id));
    }

    @PostMapping("/read-all")
    @Operation(summary = "全部标记已读")
    public Result<Map<String, Object>> markAllRead(@RequestHeader("X-User-Id") String userIdStr) {
        int count = notificationService.markAllRead(Long.parseLong(userIdStr));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("updatedCount", count);
        return Result.success(data);
    }
}