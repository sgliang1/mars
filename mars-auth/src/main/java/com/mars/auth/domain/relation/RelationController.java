package com.mars.auth.domain.relation;

import com.mars.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/relation")
public class RelationController {

    @Autowired
    private RelationService relationService;

    @GetMapping("/following")
    public Result<List<Map<String, Object>>> following(@RequestHeader("X-User-Id") String userIdStr) {
        try {
            Long userId = Long.parseLong(userIdStr);
            return Result.success(relationService.getFollowingList(userId));
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("获取关注列表失败");
        }
    }

    @GetMapping("/followers")
    public Result<List<Map<String, Object>>> followers(@RequestHeader("X-User-Id") String userIdStr) {
        try {
            Long userId = Long.parseLong(userIdStr);
            return Result.success(relationService.getFollowerList(userId));
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("获取粉丝列表失败");
        }
    }

    @PostMapping("/follow/{userId}")
    public Result<String> follow(@PathVariable("userId") Long followedId,
                                 @RequestHeader("X-User-Id") String userIdStr,
                                 @RequestHeader(value = "X-User-Name", required = false) String encodedUsername) {
        try {
            Long followerId = Long.parseLong(userIdStr);
            String username = null;
            if (encodedUsername != null) {
                username = URLDecoder.decode(encodedUsername, StandardCharsets.UTF_8.name());
            }
            relationService.follow(followerId, followedId, username);
            return Result.successMessage("关注成功");
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("关注失败");
        }
    }

    @PostMapping("/unfollow/{userId}")
    public Result<String> unfollow(@PathVariable("userId") Long followedId,
                                    @RequestHeader("X-User-Id") String userIdStr) {
        try {
            Long followerId = Long.parseLong(userIdStr);
            relationService.unfollow(followerId, followedId);
            return Result.successMessage("取消关注成功");
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("取消关注失败");
        }
    }

    @GetMapping("/status/{userId}")
    public Result<Map<String, Object>> status(@PathVariable("userId") Long targetUserId,
                                               @RequestHeader("X-User-Id") String userIdStr) {
        try {
            Long currentUserId = Long.parseLong(userIdStr);
            boolean following = relationService.isFollowing(currentUserId, targetUserId);
            boolean followedBy = relationService.isFollowedBy(currentUserId, targetUserId);
            boolean mutual = following && followedBy;
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("isFollowing", following);
            data.put("isFollowedBy", followedBy);
            data.put("isMutual", mutual);
            return Result.success(data);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("查询关注状态失败");
        }
    }

    @GetMapping("/mutual")
    public Result<List<Map<String, Object>>> mutual(@RequestHeader("X-User-Id") String userIdStr) {
        try {
            Long userId = Long.parseLong(userIdStr);
            return Result.success(relationService.getMutualFriends(userId));
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("获取好友列表失败");
        }
    }
}