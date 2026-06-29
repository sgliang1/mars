package com.interstellar.api;

import com.interstellar.api.fallback.InteractionFeignClientFallback;
import com.interstellar.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

@FeignClient(name = "interstellar-interaction", fallback = InteractionFeignClientFallback.class)
public interface InteractionFeignClient {

    @PostMapping("/comments")
    Result<?> addComment(@RequestBody Map<String, Object> comment,
                         @RequestHeader("X-User-Id") String userId,
                         @RequestHeader("X-Username") String username);
}
