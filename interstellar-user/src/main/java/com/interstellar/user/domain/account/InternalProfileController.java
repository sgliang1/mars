package com.interstellar.user.domain.account;

import com.interstellar.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部接口：供 interstellar-relation 通过 Feign 调用，更新粉丝/关注计数
 * 不走 Gateway 鉴权（内部服务间调用）
 */
@RestController
@RequestMapping("/internal/user-profile")
public class InternalProfileController {

    @Autowired
    private UserProfileMapper userProfileMapper;

    @PostMapping("/follower-count")
    public Result<Void> updateFollowerCount(@RequestParam Long userId, @RequestParam int delta) {
        userProfileMapper.updateFollowerCount(userId, delta);
        return Result.success(null);
    }

    @PostMapping("/following-count")
    public Result<Void> updateFollowingCount(@RequestParam Long userId, @RequestParam int delta) {
        userProfileMapper.updateFollowingCount(userId, delta);
        return Result.success(null);
    }
}
