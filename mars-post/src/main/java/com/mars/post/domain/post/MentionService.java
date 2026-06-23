package com.mars.post.domain.post;

import com.mars.post.domain.notification.NotificationHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MentionService {

    private static final Pattern MENTION_PATTERN = Pattern.compile("@(\\S+?)(?=\\s|$|[^\\w\\u4e00-\\u9fff])");

    @Autowired private PostMentionMapper mentionMapper;
    @Autowired private NotificationHelper notificationHelper;

    /**
     * 解析内容中的 @username，写入 mention 表并发送通知
     * @param postId 帖子ID（发帖时使用）
     * @param commentId 评论ID（评论时使用）
     * @param content 内容文本
     * @param actorId 操作者ID
     * @param actorName 操作者名称
     * @param mentionedUserIds 被@用户的ID列表（由前端在选择@时传入）
     */
    public void parseAndSave(Long postId, Long commentId, String content,
                             Long actorId, String actorName, List<Long> mentionedUserIds) {
        if (mentionedUserIds == null || mentionedUserIds.isEmpty()) {
            return;
        }

        for (Long mentionedUserId : mentionedUserIds) {
            if (mentionedUserId.equals(actorId)) continue; // 不@自己

            PostMention mention = new PostMention();
            mention.setPostId(postId);
            mention.setCommentId(commentId);
            mention.setMentionedUserId(mentionedUserId);
            mention.setCreateTime(LocalDateTime.now());
            mentionMapper.insert(mention);

            // 发送通知
            try {
                notificationHelper.notifyMention(actorId, actorName, postId, commentId, mentionedUserId);
            } catch (Exception ignored) {}
        }
    }
}