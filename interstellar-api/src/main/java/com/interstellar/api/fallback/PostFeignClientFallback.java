package com.interstellar.api.fallback;

import com.interstellar.api.PostFeignClient;
import com.interstellar.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PostFeignClientFallback implements PostFeignClient {

    @Override
    public Result<Long> getPostCount(Long userId) {
        log.warn("PostFeignClient fallback: getPostCount(userId={})", userId);
        return Result.success(0L);
    }
}