package com.interstellar.post.domain.broadcast;

import com.interstellar.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/broadcasts")
@Tag(name = "星际广播", description = "平台公告广播")
public class BroadcastController {

    @Autowired private BroadcastService broadcastService;

    @GetMapping
    @Operation(summary = "已发布广播列表")
    public Result<List<Map<String, Object>>> list(
            @RequestHeader("X-User-Id") String userIdStr,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return Result.success(broadcastService.listPublished(Long.parseLong(userIdStr), page, size));
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "标记已读")
    public Result<String> markAsRead(
            @PathVariable("id") Long broadcastId,
            @RequestHeader("X-User-Id") String userIdStr) {
        broadcastService.markAsRead(Long.parseLong(userIdStr), broadcastId);
        return Result.successMessage("已标记已读");
    }

    @GetMapping("/unread-count")
    @Operation(summary = "未读广播数量")
    public Result<Integer> unreadCount(@RequestHeader("X-User-Id") String userIdStr) {
        return Result.success(broadcastService.getUnreadCount(Long.parseLong(userIdStr)));
    }
}
