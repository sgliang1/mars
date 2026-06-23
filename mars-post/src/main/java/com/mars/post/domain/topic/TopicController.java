package com.mars.post.domain.topic;

import com.mars.common.Result;
import com.mars.post.domain.post.Post;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/topic")
@Tag(name = "话题", description = "话题浏览与帖子")
public class TopicController {

    @Autowired
    private TopicService topicService;

    @GetMapping("/list")
    @Operation(summary = "话题列表")
    public Result<List<Map<String, Object>>> list(@Parameter(description = "关键词") @RequestParam(value = "keyword", required = false) String keyword) {
        return Result.success(topicService.listTopics(keyword));
    }

    @GetMapping("/detail/{slug}")
    @Operation(summary = "话题详情")
    public Result<Map<String, Object>> detail(@Parameter(description = "话题标识") @PathVariable("slug") String slug) {
        return Result.success(topicService.getTopicDetail(slug));
    }

    @GetMapping("/posts/{slug}")
    public Result<List<Post>> posts(@PathVariable("slug") String slug,
                                    @RequestHeader(value = "X-User-Id", required = false) String userIdStr) {
        Long userId = parseUserId(userIdStr);
        return Result.success(topicService.listTopicPosts(slug, userId));
    }

    private Long parseUserId(String userIdStr) {
        if (userIdStr == null || userIdStr.isBlank()) {
            return null;
        }
        return Long.parseLong(userIdStr);
    }
}