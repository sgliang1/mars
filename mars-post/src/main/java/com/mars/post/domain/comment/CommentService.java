package com.mars.post.domain.comment;

import com.mars.post.domain.notification.NotificationHelper;
import com.mars.post.domain.post.FeedService;
import com.mars.post.domain.post.MentionService;
import com.mars.post.domain.post.Post;
import com.mars.post.domain.post.PostMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class CommentService {

    @Autowired private CommentMapper commentMapper;
    @Autowired private PostMapper postMapper;
    @Autowired private NotificationHelper notificationHelper;
    @Autowired private MentionService mentionService;
    @Autowired private FeedService feedService;

    @Transactional
    public Comment addComment(Comment comment) {
        Post post = postMapper.selectById(comment.getPostId());
        if (post == null) {
            throw new IllegalArgumentException("帖子不存在");
        }

        comment.setCreateTime(LocalDateTime.now());
        commentMapper.insert(comment);

        postMapper.incrementCommentCount(comment.getPostId());

        // 写入评论通知
        try {
            String actorName = comment.getUsername() != null ? comment.getUsername() : "匿名用户";
            notificationHelper.notifyComment(
                    comment.getUserId(), actorName,
                    comment.getPostId(), comment.getContent());
        } catch (Exception ignored) {}

        // 处理 @提及
        if (comment.getMentionUserIds() != null && !comment.getMentionUserIds().isEmpty()) {
            try {
                String actorName = comment.getUsername() != null ? comment.getUsername() : "匿名用户";
                mentionService.parseAndSave(null, comment.getId(), comment.getContent(),
                        comment.getUserId(), actorName, comment.getMentionUserIds());
            } catch (Exception ignored) {}
        }

        // 清除 Feed 缓存（评论影响热度排序）
        feedService.evictHotFeedCache();

        return comment;
    }

    /**
     * 用户删除自己的评论（软删除，保留证据）
     */
    @Transactional
    public boolean softDeleteComment(Long commentId, Long userId) {
        Comment comment = commentMapper.selectOne(
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getId, commentId)
                        .eq(Comment::getUserId, userId));
        if (comment == null) {
            return false;
        }
        comment.setDeletedAt(LocalDateTime.now());
        comment.setDeletedBy(null); // 用户自己删的，deletedBy 为 NULL
        commentMapper.updateById(comment);
        return true;
    }

    /**
     * 管理员删除评论（软删除，记录操作人）
     */
    @Transactional
    public boolean adminSoftDeleteComment(Long commentId, Long adminId) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            return false;
        }
        comment.setDeletedAt(LocalDateTime.now());
        comment.setDeletedBy(adminId);
        commentMapper.updateById(comment);
        return true;
    }
}