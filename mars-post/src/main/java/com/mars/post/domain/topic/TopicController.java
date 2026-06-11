package com.mars.post.domain.topic;

import com.mars.common.Result;
import com.mars.post.domain.post.Post;
import com.mars.post.domain.topic.TopicService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/topic")
public class TopicController {

    @Autowired
    private TopicService topicService;

    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(@RequestParam(value = "keyword", required = false) String keyword) {
        try {
            return Result.success(topicService.listTopics(keyword));
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("Failed to load topic list");
        }
    }

    @GetMapping("/detail/{slug}")
    public Result<Map<String, Object>> detail(@PathVariable String slug) {
        try {
            return Result.success(topicService.getTopicDetail(slug));
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("Failed to load topic detail");
        }
    }

    @GetMapping("/posts/{slug}")
    public Result<List<Post>> posts(@PathVariable String slug,
                                    @RequestHeader(value = "X-User-Id", required = false) String userIdStr) {
        try {
            Long userId = parseUserId(userIdStr);
            return Result.success(topicService.listTopicPosts(slug, userId));
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("Failed to load topic posts");
        }
    }

    private Long parseUserId(String userIdStr) {
        if (userIdStr == null || userIdStr.isBlank()) {
            return null;
        }
        return Long.parseLong(userIdStr);
    }
}
