package com.mars.user.domain.relation;

import com.mars.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/relation")
@Tag(name = "社交关系", description = "关注、粉丝、好友关系管理")
public class RelationController {

    @Autowired
    private RelationService relationService;

    @GetMapping("/following")
    @Operation(summary = "获取关注列表")
    public Result<List<Map<String, Object>>> following(@Parameter(description = "当前用户ID") @RequestHeader("X-User-Id") String userIdStr) {
        Long userId = Long.parseLong(userIdStr);
        return Result.success(relationService.getFollowingList(userId));
    }

    @GetMapping("/followers")
    @Operation(summary = "获取粉丝列表")
    public Result<List<Map<String, Object>>> followers(@Parameter(description = "当前用户ID") @RequestHeader("X-User-Id") String userIdStr) {
        Long userId = Long.parseLong(userIdStr);
        return Result.success(relationService.getFollowerList(userId));
    }

    @PostMapping("/follow/{userId}")
    @Operation(summary = "关注用户")
    public Result<String> follow(@Parameter(description = "目标用户ID") @PathVariable("userId") Long followedId,
                                 @RequestHeader("X-User-Id") String userIdStr,
                                 @RequestHeader(value = "X-User-Name", required = false) String encodedUsername,
                                 @RequestParam(required = false) String sourceType,
                                 @RequestParam(required = false) Long sourceId) {
        Long followerId = Long.parseLong(userIdStr);
        String username = null;
        if (encodedUsername != null) {
            username = URLDecoder.decode(encodedUsername, StandardCharsets.UTF_8);
        }
        relationService.follow(followerId, followedId, username, sourceType, sourceId);
        return Result.successMessage("关注成功");
    }

    @PostMapping("/unfollow/{userId}")
    @Operation(summary = "取消关注")
    public Result<String> unfollow(@Parameter(description = "目标用户ID") @PathVariable("userId") Long followedId,
                                    @RequestHeader("X-User-Id") String userIdStr) {
        Long followerId = Long.parseLong(userIdStr);
        relationService.unfollow(followerId, followedId);
        return Result.successMessage("取消关注成功");
    }

    @GetMapping("/status/{userId}")
    @Operation(summary = "查询关注状态", description = "返回 isFollowing/isFollowedBy/isMutual")
    public Result<Map<String, Object>> status(@Parameter(description = "目标用户ID") @PathVariable("userId") Long targetUserId,
                                               @RequestHeader("X-User-Id") String userIdStr) {
        Long currentUserId = Long.parseLong(userIdStr);
        boolean following = relationService.isFollowing(currentUserId, targetUserId);
        boolean followedBy = relationService.isFollowedBy(currentUserId, targetUserId);
        boolean mutual = following && followedBy;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("isFollowing", following);
        data.put("isFollowedBy", followedBy);
        data.put("isMutual", mutual);
        return Result.success(data);
    }

    @GetMapping("/mutual")
    @Operation(summary = "获取好友列表", description = "双向关注的好友")
    public Result<List<Map<String, Object>>> mutual(@RequestHeader("X-User-Id") String userIdStr) {
        Long userId = Long.parseLong(userIdStr);
        return Result.success(relationService.getMutualFriends(userId));
    }

    // ==================== 拉黑 ====================

    @PostMapping("/block/{userId}")
    @Operation(summary = "拉黑用户")
    public Result<String> block(@Parameter(description = "目标用户ID") @PathVariable("userId") Long blockedId,
                                @RequestHeader("X-User-Id") String userIdStr) {
        relationService.block(Long.parseLong(userIdStr), blockedId);
        return Result.successMessage("拉黑成功");
    }

    @PostMapping("/unblock/{userId}")
    @Operation(summary = "取消拉黑")
    public Result<String> unblock(@Parameter(description = "目标用户ID") @PathVariable("userId") Long blockedId,
                                  @RequestHeader("X-User-Id") String userIdStr) {
        relationService.unblock(Long.parseLong(userIdStr), blockedId);
        return Result.successMessage("取消拉黑成功");
    }

    @GetMapping("/blocks")
    @Operation(summary = "拉黑列表")
    public Result<List<Map<String, Object>>> blockList(@RequestHeader("X-User-Id") String userIdStr) {
        return Result.success(relationService.getBlockList(Long.parseLong(userIdStr)));
    }

    // ==================== 静音 ====================

    @PostMapping("/mute/{userId}")
    @Operation(summary = "静音用户")
    public Result<String> mute(@Parameter(description = "目标用户ID") @PathVariable("userId") Long mutedId,
                               @RequestHeader("X-User-Id") String userIdStr) {
        relationService.mute(Long.parseLong(userIdStr), mutedId);
        return Result.successMessage("静音成功");
    }

    @PostMapping("/unmute/{userId}")
    @Operation(summary = "取消静音")
    public Result<String> unmute(@Parameter(description = "目标用户ID") @PathVariable("userId") Long mutedId,
                                 @RequestHeader("X-User-Id") String userIdStr) {
        relationService.unmute(Long.parseLong(userIdStr), mutedId);
        return Result.successMessage("取消静音成功");
    }

    @GetMapping("/mutes")
    @Operation(summary = "静音列表")
    public Result<List<Map<String, Object>>> muteList(@RequestHeader("X-User-Id") String userIdStr) {
        return Result.success(relationService.getMuteList(Long.parseLong(userIdStr)));
    }
}