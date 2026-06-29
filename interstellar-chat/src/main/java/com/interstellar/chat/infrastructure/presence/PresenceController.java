package com.interstellar.chat.infrastructure.presence;

import com.interstellar.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/presence")
@Tag(name = "在线状态", description = "用户在线状态与最近活跃查询")
public class PresenceController {

    @Autowired
    private PresenceService presenceService;

    @GetMapping("/{userId}")
    @Operation(summary = "查询单个用户在线状态")
    public Result<Map<String, Object>> getPresence(
            @RequestHeader("X-User-Id") String viewerId,
            @PathVariable Long userId) {
        return Result.success(presenceService.getPresence(userId));
    }

    @GetMapping("/batch")
    @Operation(summary = "批量查询用户在线状态")
    public Result<List<Map<String, Object>>> batchPresence(
            @RequestHeader("X-User-Id") String viewerId,
            @RequestParam("userIds") String userIdsStr) {
        List<Long> userIds = Arrays.stream(userIdsStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toList());
        return Result.success(presenceService.batchPresence(userIds));
    }
}
