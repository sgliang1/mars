package com.interstellar.api.fallback;

import com.interstellar.api.UserFeignClient;
import com.interstellar.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class UserFeignClientFallback implements UserFeignClient {

    @Override
    public Result<List<Map<String, Object>>> searchUsers(String q, int page, int size) {
        log.warn("UserFeignClient fallback: searchUsers(q={})", q);
        return Result.success(Collections.emptyList());
    }

    @Override
    public Result<Void> addReputation(Map<String, Object> body) {
        log.warn("UserFeignClient fallback: addReputation userId={}", body.get("userId"));
        return Result.success(null);
    }
}