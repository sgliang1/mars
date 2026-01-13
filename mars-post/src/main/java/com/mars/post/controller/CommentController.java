package com.mars.post.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mars.common.Result;
import com.mars.post.entity.Comment;
import com.mars.post.mapper.CommentMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/comment")
public class CommentController {

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private com.mars.post.mapper.PostMapper postMapper;

    /**
     * 发表评论
     * 修改说明：增加 URL 解码逻辑，防止用户名乱码
     */
    @PostMapping("/add")
    public Result add(@RequestBody Comment comment,
                      @RequestHeader(value = "X-User-Id", required = false) String userIdStr,
                      @RequestHeader(value = "X-User-Name", required = false) String encodedUsername) {

        try {
            // 1. 自动填充 UserID
            if (comment.getUserId() == null && userIdStr != null) {
                comment.setUserId(Long.parseLong(userIdStr));
            }

            // 2. 自动填充 Username (需解码)
            if (comment.getUsername() == null && encodedUsername != null) {
                String username = URLDecoder.decode(encodedUsername, StandardCharsets.UTF_8.name());
                comment.setUsername(username);
            }

            comment.setCreateTime(LocalDateTime.now());
            commentMapper.insert(comment);

            // 3. 帖子评论数 +1
            if (comment.getPostId() != null) {
                com.mars.post.entity.Post post = postMapper.selectById(comment.getPostId());
                if (post != null) {
                    int count = post.getCommentCount() == null ? 0 : post.getCommentCount();
                    post.setCommentCount(count + 1);
                    postMapper.updateById(post);
                }
            }

            return Result.success("评论成功");

        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("评论失败");
        }
    }

    /**
     * 评论列表
     */
    @GetMapping("/list/{postId}")
    public Result list(@PathVariable("postId") Long postId) {
        List<Comment> list = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getPostId, postId)
                .orderByDesc(Comment::getCreateTime));
        return Result.success(list);
    }
}