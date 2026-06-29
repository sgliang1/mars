package com.interstellar.chat.domain.room;

import com.interstellar.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/club-channels")
@Tag(name = "俱乐部通讯", description = "俱乐部间通讯频段管理")
public class ClubChannelController {

    @Autowired
    private ClubChannelService clubChannelService;

    @PostMapping
    @Operation(summary = "发起通讯邀请")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body,
                                              @RequestHeader("X-User-Id") String userIdStr) {
        Long clubAId = Long.parseLong(body.get("clubAId").toString());
        Long clubBId = Long.parseLong(body.get("clubBId").toString());
        return Result.success(clubChannelService.createChannel(Long.parseLong(userIdStr), clubAId, clubBId));
    }

    @GetMapping
    @Operation(summary = "俱乐部的通讯列表")
    public Result<List<Map<String, Object>>> list(@RequestParam("clubId") Long clubId) {
        return Result.success(clubChannelService.listChannels(clubId));
    }

    @PostMapping("/{channelId}/close")
    @Operation(summary = "关闭通讯")
    public Result<String> close(@PathVariable("channelId") Long channelId,
                                @RequestHeader("X-User-Id") String userIdStr) {
        clubChannelService.closeChannel(Long.parseLong(userIdStr), channelId);
        return Result.successMessage("已关闭通讯");
    }
}
