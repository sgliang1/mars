package com.interstellar.api;

import com.interstellar.api.fallback.UserFeignClientFallback;
import com.interstellar.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(name = "interstellar-user", fallback = UserFeignClientFallback.class)
public interface UserFeignClient {

    @GetMapping("/user/search")
    Result<List<Map<String, Object>>> searchUsers(
            @RequestParam("q") String q,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size);

    @PostMapping("/internal/reputation/add")
    Result<Void> addReputation(@RequestBody Map<String, Object> body);
}