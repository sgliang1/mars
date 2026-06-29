package com.interstellar.api.fallback;

import com.interstellar.api.InteractionFeignClient;
import com.interstellar.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class InteractionFeignClientFallback implements InteractionFeignClient {

    @Override
    public Result<?> addComment(Map<String, Object> comment, String userId, String username) {
        log.warn("InteractionFeignClient.addComment 降级: userId={}", userId);
        return Result.fail("评论服务不可用");
    }
}
