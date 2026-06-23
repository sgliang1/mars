package com.mars.post.domain.post;

import com.mars.common.mq.NotificationMessage;
import com.mars.post.mq.NotificationProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MentionService {

    @Autowired private PostMentionMapper mentionMapper;
    @Autowired private NotificationProducer notificationProducer;

    /**
     * 解析内容中的 @username，写入 mention 表并发送通知
     * 通知通过 MQ 异步发送到 mars-interaction 消费
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

            // 通知通过 MQ 异步发送到 mars-interaction 消费
            try {
                String sourceId = postId != null ? String.valueOf(postId) : String.valueOf(commentId);
                String sourceType = postId != null ? "mention_post" : "mention_comment";

                NotificationMessage msg = new NotificationMessage(
                        mentionedUserId, "interaction", actorName != null ? actorName : "匿名",
                        "{\"actorId\":\"" + actorId + "\"}",
                        sourceType, sourceId);
                msg.setActorId(actorId);
                msg.setPostId(postId);
                notificationProducer.sendInteraction(msg);
            } catch (Exception ignored) {}
        }
    }
}
