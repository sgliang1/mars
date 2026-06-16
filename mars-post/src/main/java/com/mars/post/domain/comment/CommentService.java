package com.mars.post.domain.comment;

import com.mars.post.domain.notification.NotificationHelper;
import com.mars.post.domain.post.Post;
import com.mars.post.domain.post.PostMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class CommentService {

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private NotificationHelper notificationHelper;

    /**
     * 发表评论 — 事务保证评论插入 + 帖子计数原子更新
     */
    @Transactional
    public Comment addComment(Comment comment) {
        // 校验帖子存在
        Post post = postMapper.selectById(comment.getPostId());
        if (post == null) {
            throw new IllegalArgumentException("帖子不存在");
        }

        comment.setCreateTime(LocalDateTime.now());
        commentMapper.insert(comment);

        // 原子计数器更新
        postMapper.incrementCommentCount(comment.getPostId());

        // 写入评论通知（不影响主流程）
        try {
            String actorName = comment.getUsername() != null ? comment.getUsername() : "匿名用户";
            notificationHelper.notifyComment(
                    comment.getUserId(), actorName,
                    comment.getPostId(), comment.getContent());
        } catch (Exception ignored) {}

        return comment;
    }
}