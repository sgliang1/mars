package com.interstellar.api.fallback;

import com.interstellar.api.RelationFeignClient;
import com.interstellar.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * RelationFeignClient 熔断降级
 * interstellar-user 不可用时返回成功（计数不更新，后续可通过定时任务补偿）
 */
@Slf4j
@Component
public class RelationFeignClientFallback implements RelationFeignClient {

    @Override
    public Result<Void> updateFollowerCount(Long userId, int delta) {
        log.warn("interstellar-user 不可用，跳过 follower 计数更新: userId={}, delta={}", userId, delta);
        return Result.success(null);
    }

    @Override
    public Result<Void> updateFollowingCount(Long userId, int delta) {
        log.warn("interstellar-user 不可用，跳过 following 计数更新: userId={}, delta={}", userId, delta);
        return Result.success(null);
    }
}
