package com.mars.user.domain.relation;

import com.mars.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/relation/settings")
@Tag(name = "关系可见性设置", description = "控制对特定用户的帖子/资料/私信可见性")
public class RelationSettingController {

    @Autowired
    private RelationSettingService relationSettingService;

    @GetMapping("/{targetUserId}")
    @Operation(summary = "获取对某用户的可见性设置")
    public Result<UserRelationSetting> getSetting(@RequestHeader("X-User-Id") String userIdStr,
                                                   @PathVariable Long targetUserId) {
        Long userId = Long.parseLong(userIdStr);
        return Result.success(relationSettingService.getOrCreateSetting(userId, targetUserId));
    }

    @PutMapping("/{targetUserId}")
    @Operation(summary = "更新对某用户的可见性设置")
    public Result<String> updateSetting(@RequestHeader("X-User-Id") String userIdStr,
                                         @PathVariable Long targetUserId,
                                         @Parameter(description = "帖子可见 1=是 0=否") @RequestParam(required = false) Integer postVisible,
                                         @Parameter(description = "资料可见 1=是 0=否") @RequestParam(required = false) Integer profileVisible,
                                         @Parameter(description = "允许私信 1=是 0=否") @RequestParam(required = false) Integer canMessage) {
        Long userId = Long.parseLong(userIdStr);
        relationSettingService.updateSetting(userId, targetUserId, postVisible, profileVisible, canMessage);
        return Result.successMessage("设置已更新");
    }

    @GetMapping("/{targetUserId}/check/post")
    @Operation(summary = "检查能否查看对方帖子")
    public Result<Boolean> checkPostVisible(@RequestHeader("X-User-Id") String userIdStr,
                                             @PathVariable Long targetUserId) {
        Long userId = Long.parseLong(userIdStr);
        return Result.success(relationSettingService.canViewPosts(userId, targetUserId));
    }

    @GetMapping("/{targetUserId}/check/profile")
    @Operation(summary = "检查能否查看对方资料")
    public Result<Boolean> checkProfileVisible(@RequestHeader("X-User-Id") String userIdStr,
                                                @PathVariable Long targetUserId) {
        Long userId = Long.parseLong(userIdStr);
        return Result.success(relationSettingService.canViewProfile(userId, targetUserId));
    }

    @GetMapping("/{targetUserId}/check/message")
    @Operation(summary = "检查能否给对方发私信")
    public Result<Boolean> checkCanMessage(@RequestHeader("X-User-Id") String userIdStr,
                                            @PathVariable Long targetUserId) {
        Long userId = Long.parseLong(userIdStr);
        return Result.success(relationSettingService.canSendMessage(userId, targetUserId));
    }
}