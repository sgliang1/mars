package com.interstellar.post.domain.poll;

import com.interstellar.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/polls")
@Tag(name = "投票", description = "投票创建、投票、查看结果")
class PollController {

    @Autowired private PollService pollService;

    @PostMapping("/{pollId}/vote")
    @Operation(summary = "投票", description = "单选传1个optionId，多选传多个")
    public Result<Map<String, Object>> vote(
            @PathVariable("pollId") Long pollId,
            @RequestBody List<Long> optionIds,
            @RequestHeader("X-User-Id") String userIdStr) {
        Long userId = Long.parseLong(userIdStr);
        Map<String, Object> result = pollService.vote(pollId, userId, optionIds);
        return Result.success(result);
    }

    @GetMapping("/{pollId}")
    @Operation(summary = "查看投票结果")
    public Result<Map<String, Object>> getResult(
            @PathVariable("pollId") Long pollId,
            @RequestHeader(value = "X-User-Id", required = false) String userIdStr) {
        Long userId = userIdStr != null ? Long.parseLong(userIdStr) : 0L;
        Map<String, Object> result = pollService.getPollResult(pollId, userId);
        return Result.success(result);
    }

    @GetMapping("/post/{postId}")
    @Operation(summary = "获取帖子的投票", description = "根据帖子ID获取投票内容和结果")
    public Result<Map<String, Object>> getByPostId(
            @PathVariable("postId") Long postId,
            @RequestHeader(value = "X-User-Id", required = false) String userIdStr) {
        Long userId = userIdStr != null ? Long.parseLong(userIdStr) : 0L;
        Poll poll = pollService.getPollByPostId(postId);
        if (poll == null) return Result.fail("该帖子无投票");
        Map<String, Object> result = pollService.getPollResult(poll.getId(), userId);
        return Result.success(result);
    }
}