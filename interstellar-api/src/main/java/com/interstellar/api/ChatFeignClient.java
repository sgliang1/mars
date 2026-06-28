package com.interstellar.api;

import com.interstellar.api.fallback.ChatFeignClientFallback;
import com.interstellar.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "interstellar-chat", fallback = ChatFeignClientFallback.class)
public interface ChatFeignClient {

    @GetMapping("/rooms/{roomId}/is-member")
    Result<Boolean> isMember(@PathVariable("roomId") Long roomId,
                             @RequestParam("userId") Long userId);

    @GetMapping("/rooms/user-club-name")
    Result<String> getUserClubName(@RequestParam("userId") Long userId);
}
