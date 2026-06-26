package com.interstellar.post.domain.post;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interstellar.common.Result;
import com.interstellar.post.domain.post.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/posts")
@Tag(name = "转发", description = "帖子转发管理")
public class RepostController {

    @Autowired private RepostService repostService;
    @Autowired private PostMapper postMapper;
    @Autowired private PostRepostMapper repostMapper;
    @Autowired private PostImageMapper postImageMapper;

    @PostMapping("/{postId}/repost")
    @Operation(summary = "转发帖子")
    public Result repost(@Parameter(description = "原帖ID") @PathVariable("postId") Long postId,
                         @RequestBody(required = false) Map<String, String> body,
                         @RequestHeader("X-User-Id") String userIdStr,
                         @RequestHeader(value = "X-User-Name", required = false) String encodedUsername) {
        Long userId = Long.parseLong(userIdStr);
        String quoteContent = body != null ? body.getOrDefault("quoteContent", "") : "";

        PostRepost result = repostService.repost(userId, postId, quoteContent);
        if (result == null) {
            return Result.fail("你已经转发过该帖子");
        }
        return Result.successMessage("转发成功");
    }

    @GetMapping("/{postId}/reposts")
    @Operation(summary = "帖子转发列表")
    public Result listReposts(@Parameter(description = "原帖ID") @PathVariable("postId") Long postId,
                              @RequestParam(value = "page", defaultValue = "1") int page,
                              @RequestParam(value = "size", defaultValue = "20") int size) {
        List<PostRepost> reposts = repostService.listByPost(postId, page, size);
        if (reposts.isEmpty()) {
            return Result.success(Collections.emptyList());
        }

        Set<Long> userIds = reposts.stream().map(PostRepost::getUserId).collect(Collectors.toSet());
        Set<Long> originalIds = reposts.stream().map(PostRepost::getOriginalPostId).collect(Collectors.toSet());

        Map<Long, Post> postMap = postMapper.selectBatchIds(originalIds).stream()
                .collect(Collectors.toMap(Post::getId, p -> p, (a, b) -> a));

        List<Map<String, Object>> records = reposts.stream().map(r -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", r.getId());
            item.put("userId", r.getUserId());
            item.put("originalPostId", r.getOriginalPostId());
            item.put("quoteContent", r.getQuoteContent());
            item.put("createTime", r.getCreateTime());
            Post original = postMap.get(r.getOriginalPostId());
            if (original != null) {
                item.put("originalTitle", original.getTitle());
                item.put("originalUsername", original.getUsername());
            }
            return item;
        }).collect(Collectors.toList());

        return Result.success(records);
    }
}