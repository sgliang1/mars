package com.interstellar.interaction.domain.comment;

import com.interstellar.interaction.domain.notification.NotificationHelper;
import com.interstellar.interaction.domain.post.PostEntity;
import com.interstellar.interaction.domain.post.PostMapper;
import com.interstellar.interaction.domain.post.PostMentionEntity;
import com.interstellar.interaction.domain.post.PostMentionMapper;
import com.interstellar.common.util.SanitizeUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class CommentService {

    @Autowired private CommentMapper commentMapper;
    @Autowired private PostMapper postMapper;
    @Autowired private PostMentionMapper postMentionMapper;
    @Autowired private NotificationHelper notificationHelper;
    @Autowired private RedisTemplate<String, Object> redisTemplate;

    @Transactional
    public Comment addComment(Comment comment) {
        PostEntity post = postMapper.selectById(comment.getPostId());
        if (post == null) {
            throw new IllegalArgumentException("帖子不存在");
        }

        // XSS 净化
        comment.setContent(SanitizeUtil.stripHtml(comment.getContent()));

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

        // 处理 @提及（内联逻辑，替代原 MentionService）
        if (comment.getMentionUserIds() != null && !comment.getMentionUserIds().isEmpty()) {
            try {
                String actorName = comment.getUsername() != null ? comment.getUsername() : "匿名用户";
                for (Long mentionedUserId : comment.getMentionUserIds()) {
                    if (mentionedUserId.equals(comment.getUserId())) continue; // 不@自己

                    PostMentionEntity mention = new PostMentionEntity();
                    mention.setPostId(null);
                    mention.setCommentId(comment.getId());
                    mention.setMentionedUserId(mentionedUserId);
                    mention.setCreateTime(LocalDateTime.now());
                    postMentionMapper.insert(mention);

                    notificationHelper.notifyMention(
                            comment.getUserId(), actorName,
                            null, comment.getId(), mentionedUserId);
                }
            } catch (Exception ignored) {}
        }

        // Feed 缓存失效：Redis Pub/Sub 事件驱动（标准模式）
        try {
            redisTemplate.convertAndSend("interstellar:event:feed:evict", "hot");
        } catch (Exception ignored) {}

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
        comment.setDeletedBy(null);
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
