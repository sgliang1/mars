package com.mars.api;

import com.mars.api.fallback.UserFeignClientFallback;
import com.mars.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(name = "mars-user", fallback = UserFeignClientFallback.class)
public interface UserFeignClient {

    @GetMapping("/user/search")
    Result<List<Map<String, Object>>> searchUsers(
            @RequestParam("q") String q,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size);
}