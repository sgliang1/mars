package com.mars.post.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mars.common.Result;
import com.mars.post.entity.Comment;
import com.mars.post.mapper.CommentMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/comment")
public class CommentController {

    @Autowired
    private CommentMapper commentMapper;

    // ✅ 注入 PostMapper 用于更新评论数
    @Autowired
    private com.mars.post.mapper.PostMapper postMapper;

    @PostMapping("/add")
    public Result add(@RequestBody Comment comment,
                      @RequestHeader(value = "X-User-Id", required = false) String userIdStr,
                      @RequestHeader(value = "X-User-Name", required = false) String username) {

        if (comment.getUserId() == null && userIdStr != null) {
            comment.setUserId(Long.parseLong(userIdStr));
        }
        if (comment.getUsername() == null && username != null) {
            comment.setUsername(username);
        }
        comment.setCreateTime(LocalDateTime.now());
        commentMapper.insert(comment);

        // ✅ 核心改动：帖子评论数 +1
        if (comment.getPostId() != null) {
            com.mars.post.entity.Post post = postMapper.selectById(comment.getPostId());
            if (post != null) {
                int count = post.getCommentCount() == null ? 0 : post.getCommentCount();
                post.setCommentCount(count + 1);
                postMapper.updateById(post);
            }
        }

        return Result.success("评论成功");
    }

    @GetMapping("/list/{postId}")
    public Result list(@PathVariable("postId") Long postId) {
        List<Comment> list = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getPostId, postId)
                .orderByDesc(Comment::getCreateTime));
        return Result.success(list);
    }
}