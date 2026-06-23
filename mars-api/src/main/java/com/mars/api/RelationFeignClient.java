package com.mars.api;

import com.mars.api.fallback.RelationFeignClientFallback;
import com.mars.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * mars-relation → mars-user 的 Feign 客户端
 * 用于社交关系变更时更新 user_profile 中的粉丝/关注计数
 */
@FeignClient(name = "mars-user", contextId = "relationFeignClient", fallback = RelationFeignClientFallback.class)
public interface RelationFeignClient {

    @PostMapping("/internal/user-profile/follower-count")
    Result<Void> updateFollowerCount(@RequestParam("userId") Long userId, @RequestParam("delta") int delta);

    @PostMapping("/internal/user-profile/following-count")
    Result<Void> updateFollowingCount(@RequestParam("userId") Long userId, @RequestParam("delta") int delta);
}
