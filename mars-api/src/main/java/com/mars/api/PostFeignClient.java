package com.mars.api;

import com.mars.api.fallback.PostFeignClientFallback;
import com.mars.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "mars-post", fallback = PostFeignClientFallback.class)
public interface PostFeignClient {

    @GetMapping("/posts/count/{userId}")
    Result<Long> getPostCount(@PathVariable("userId") Long userId);
}