package com.interstellar.api;

import com.interstellar.api.fallback.PostFeignClientFallback;
import com.interstellar.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "interstellar-post", fallback = PostFeignClientFallback.class)
public interface PostFeignClient {

    @GetMapping("/posts/count/{userId}")
    Result<Long> getPostCount(@PathVariable("userId") Long userId);
}