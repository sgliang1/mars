package com.mars.post.domain.comment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mars.common.Result;
import com.mars.common.cache.CacheKeys;
import com.mars.common.cache.CacheService;
import com.mars.post.domain.post.Post;
import com.mars.post.domain.post.PostMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/comments")
@Tag(name = "评论", description = "帖子评论管理")
public class CommentController {

    @Autowired private CommentService commentService;
    @Autowired private CommentMapper commentMapper;
    @Autowired private CommentLikeMapper commentLikeMapper;
    @Autowired private PostMapper postMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private CacheService cacheService;

    @DeleteMapping("/{id}")
    @Operation(summary = "删除自己的评论")
    public Result<Void> deleteMyComment(
            @Parameter(description = "评论ID") @PathVariable("id") Long commentId,
            @RequestHeader("X-User-Id") String userIdStr) {
        Long userId = Long.parseLong(userIdStr);
        boolean ok = commentService.softDeleteComment(commentId, userId);
        if (!ok) {
            return Result.fail("评论不存在或无权删除");
        }
        return Result.successMessage("评论已删除");
    }

    @PostMapping("")
    @Operation(summary = "发表评论")
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

            // 信用分检查：低于 60 分禁止评论
            if (comment.getUserId() != null) {
                Object creditObj = cacheService.get(CacheKeys.key(CacheKeys.USER_CREDIT, comment.getUserId()));
                if (creditObj != null) {
                    int credit = Integer.parseInt(creditObj.toString());
                    if (credit < 60) {
                        return Result.fail("信用分不足，暂时无法评论");
                    }
                }
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
    @Operation(summary = "帖子评论列表", description = "支持 sort=hot(按点赞数) 或 sort=time(按时间，默认)")
    public Result list(@Parameter(description = "帖子ID") @PathVariable("postId") Long postId,
                       @RequestParam(value = "page", defaultValue = "1") int page,
                       @RequestParam(value = "size", defaultValue = "20") int size,
                       @RequestParam(value = "sort", defaultValue = "time") String sort) {
        Page<Comment> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<Comment>()
                .eq(Comment::getPostId, postId)
                .isNull(Comment::getDeletedAt);

        if ("hot".equals(sort)) {
            wrapper.orderByDesc(Comment::getLikeCount, Comment::getCreateTime);
        } else {
            wrapper.orderByDesc(Comment::getCreateTime);
        }

        Page<Comment> result = commentMapper.selectPage(pageParam, wrapper);
        List<Comment> comments = result.getRecords();

        // 批量补充用户头像
        enrichAvatars(comments);

        return Result.success(comments);
    }

    private void enrichAvatars(List<Comment> comments) {
        Set<Long> userIds = comments.stream()
                .map(Comment::getUserId)
                .filter(uid -> uid != null)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) return;

        String inSql = userIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        List<Map<String, Object>> profiles = jdbcTemplate.queryForList(
                "SELECT user_id, avatar_url FROM user_profile WHERE user_id IN (" + inSql + ")");

        Map<Long, String> avatarMap = new HashMap<>();
        for (Map<String, Object> row : profiles) {
            Long uid = ((Number) row.get("user_id")).longValue();
            String avatarUrl = (String) row.get("avatar_url");
            if (avatarUrl != null && !avatarUrl.isBlank()) {
                avatarMap.put(uid, avatarUrl);
            }
        }

        for (Comment c : comments) {
            if (c.getUserId() != null && avatarMap.containsKey(c.getUserId())) {
                c.setAvatar(avatarMap.get(c.getUserId()));
            }
        }
    }

    /**
     * 评论点赞/取消点赞
     */
    @PostMapping("/{commentId}/like")
    @Operation(summary = "评论点赞/取消点赞")
    public Result likeComment(@Parameter(description = "评论ID") @PathVariable("commentId") Long commentId,
                              @RequestHeader("X-User-Id") String userIdStr) {
        try {
            Long userId = Long.parseLong(userIdStr);

            CommentLike existing = commentLikeMapper.selectOne(new LambdaQueryWrapper<CommentLike>()
                    .eq(CommentLike::getCommentId, commentId)
                    .eq(CommentLike::getUserId, userId));

            if (existing != null) {
                // 取消点赞
                commentLikeMapper.deleteById(existing.getId());
                commentLikeMapper.decrementLikeCount(commentId);
                return Result.successMessage("取消点赞");
            } else {
                // 点赞
                CommentLike like = new CommentLike();
                like.setCommentId(commentId);
                like.setUserId(userId);
                like.setCreateTime(LocalDateTime.now());
                commentLikeMapper.insert(like);
                commentLikeMapper.incrementLikeCount(commentId);
                return Result.successMessage("点赞成功");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("操作失败");
        }
    }

    @GetMapping("/mine")
    @Operation(summary = "我的评论列表")
    public Result<List<Map<String, Object>>> mine(@RequestHeader("X-User-Id") String userIdStr) {
        Long userId = Long.parseLong(userIdStr);
        List<Comment> comments = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getUserId, userId)
                .isNull(Comment::getDeletedAt)
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
            item.put("likeCount", comment.getLikeCount());
            item.put("createTime", comment.getCreateTime());
            return item;
        }).toList();

        return Result.success(records);
    }
}