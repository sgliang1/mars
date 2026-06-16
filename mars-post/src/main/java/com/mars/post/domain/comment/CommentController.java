package com.mars.post.domain.comment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mars.common.Result;
import com.mars.post.domain.post.Post;
import com.mars.post.domain.post.PostMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/comments")
public class CommentController {

    @Autowired
    private CommentService commentService;

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private PostMapper postMapper;

    @PostMapping("")
    public Result add(@RequestBody Comment comment,
                      @RequestHeader(value = "X-User-Id", required = false) String userIdStr,
                      @RequestHeader(value = "X-User-Name", required = false) String encodedUsername) {
        try {
            if (comment.getUserId() == null && userIdStr != null) {
                comment.setUserId(Long.parseLong(userIdStr));
            }
            if (comment.getUsername() == null && encodedUsername != null) {
                String username = URLDecoder.decode(encodedUsername, StandardCharsets.UTF_8.name());
                comment.setUsername(username);
            }

            commentService.addComment(comment);
            return Result.successMessage("评论成功");

        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("评论失败");
        }
    }

    @GetMapping("/post/{postId}")
    public Result list(@PathVariable("postId") Long postId,
                       @RequestParam(value = "page", defaultValue = "1") int page,
                       @RequestParam(value = "size", defaultValue = "20") int size) {
        Page<Comment> pageParam = new Page<>(page, size);
        Page<Comment> result = commentMapper.selectPage(pageParam, new LambdaQueryWrapper<Comment>()
                .eq(Comment::getPostId, postId)
                .orderByDesc(Comment::getCreateTime));
        return Result.success(result.getRecords());
    }

    @GetMapping("/mine")
    public Result<List<Map<String, Object>>> mine(@RequestHeader("X-User-Id") String userIdStr) {
        Long userId = Long.parseLong(userIdStr);
        List<Comment> comments = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getUserId, userId)
                .orderByDesc(Comment::getCreateTime));

        if (comments.isEmpty()) {
            return Result.success(Collections.emptyList());
        }

        Set<Long> postIds = comments.stream()
                .map(Comment::getPostId)
                .filter(postId -> postId != null)
                .collect(Collectors.toSet());

        Map<Long, Post> postMap = postMapper.selectBatchIds(postIds).stream()
                .collect(Collectors.toMap(Post::getId, post -> post, (left, right) -> left));

        List<Map<String, Object>> records = comments.stream().map(comment -> {
            Post post = postMap.get(comment.getPostId());
            Map<String, Object> item = new HashMap<>();
            item.put("id", comment.getId());
            item.put("postId", comment.getPostId());
            item.put("postTitle", post != null ? post.getTitle() : "帖子已删除");
            item.put("content", comment.getContent());
            item.put("parentId", comment.getParentId());
            item.put("createTime", comment.getCreateTime());
            return item;
        }).toList();

        return Result.success(records);
    }
}