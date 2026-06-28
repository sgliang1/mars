package com.interstellar.api.fallback;

import com.interstellar.api.ChatFeignClient;
import com.interstellar.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ChatFeignClientFallback implements ChatFeignClient {

    @Override
    public Result<Boolean> isMember(Long roomId, Long userId) {
        log.warn("ChatFeignClient.isMember 降级: roomId={}, userId={}", roomId, userId);
        return Result.success(false);
    }

    @Override
    public Result<String> getUserClubName(Long userId) {
        log.warn("ChatFeignClient.getUserClubName 降级: userId={}", userId);
        return Result.success("");
    }
}
