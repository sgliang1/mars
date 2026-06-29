package com.interstellar.user.domain.badge;

import com.interstellar.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@Tag(name = "星际勋章", description = "勋章系统")
public class BadgeController {

    @Autowired private BadgeService badgeService;

    @GetMapping("/badges/catalog")
    @Operation(summary = "勋章目录", description = "所有勋章定义（含用户是否已获得）")
    public Result<List<Map<String, Object>>> catalog(@RequestHeader("X-User-Id") String userIdStr) {
        return Result.success(badgeService.getCatalog(Long.parseLong(userIdStr)));
    }

    @GetMapping("/badges/{userId}")
    @Operation(summary = "用户勋章列表")
    public Result<List<Map<String, Object>>> userBadges(@PathVariable("userId") Long userId) {
        return Result.success(badgeService.getUserBadges(userId));
    }

    @GetMapping("/badges/{userId}/wall")
    @Operation(summary = "勋章墙", description = "按类别分组的勋章展示")
    public Result<Map<String, List<Map<String, Object>>>> badgeWall(@PathVariable("userId") Long userId) {
        return Result.success(badgeService.getBadgeWall(userId));
    }
}
