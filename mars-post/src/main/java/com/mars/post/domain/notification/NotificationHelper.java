package com.mars.post.domain.notification;

import com.mars.common.mq.NotificationMessage;
import com.mars.post.domain.filter.UserFilterService;
import com.mars.post.domain.post.Post;
import com.mars.post.domain.post.PostMapper;
import com.mars.post.mq.NotificationProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.mars.common.model.Notification;

@Component
public class NotificationHelper {

    @Autowired
    private InteractionNotificationMapper notificationMapper;

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private NotificationProducer notificationProducer;

    @Autowired
    private UserFilterService userFilterService;

    /**
     * 创建点赞通知（MQ 异步写入）
     */
    public void notifyLike(Long actorId, String actorName, Long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null || post.getUserId().equals(actorId)) {
            return;
        }
        // 被拉黑时不发送通知
        if (userFilterService.isFiltered(post.getUserId(), actorId)) {
            return;
        }

        NotificationMessage msg = new NotificationMessage(
                post.getUserId(), "interaction", truncate(actorName, 128),
                buildContentJson(actorId, postId, post.getTitle(), null),
                "like", String.valueOf(postId));
        notificationProducer.sendInteraction(msg);
    }

    /**
     * 创建评论通知（MQ 异步写入）
     */
    public void notifyComment(Long actorId, String actorName, Long postId, String commentContent) {
        Post post = postMapper.selectById(postId);
        if (post == null || post.getUserId().equals(actorId)) {
            return;
        }
        // 被拉黑时不发送通知
        if (userFilterService.isFiltered(post.getUserId(), actorId)) {
            return;
        }

        NotificationMessage msg = new NotificationMessage(
                post.getUserId(), "interaction", truncate(actorName, 128),
                buildContentJson(actorId, postId, post.getTitle(), truncate(commentContent, 60)),
                "comment", String.valueOf(postId));
        notificationProducer.sendInteraction(msg);
    }

    /**
     * 创建@提及通知（MQ 异步写入）
     */
    public void notifyMention(Long actorId, String actorName, Long postId, Long commentId, Long mentionedUserId) {
        // 被拉黑时不发送通知
        if (userFilterService.isFiltered(mentionedUserId, actorId)) {
            return;
        }
        String sourceId = postId != null ? String.valueOf(postId) : String.valueOf(commentId);
        String sourceType = postId != null ? "mention_post" : "mention_comment";

        NotificationMessage msg = new NotificationMessage(
                mentionedUserId, "interaction", truncate(actorName, 128),
                buildContentJson(actorId, postId, null, null),
                sourceType, sourceId);
        notificationProducer.sendInteraction(msg);
    }

    /**
     * 创建关注通知（直接写入，关注操作在 mars-user 服务中）
     * 此方法供需要同步通知的场景使用
     */
    public void notifyFollowDirect(Long followerId, String followerName, Long followedId) {
        if (followerId.equals(followedId)) {
            return;
        }

        Notification n = new Notification();
        n.setUserId(followedId);
        n.setCategory("interaction");
        n.setTitle(truncate(followerName, 128));
        n.setContent("{\"actorId\":\"" + followerId + "\"}");
        n.setSourceType("follow");
        n.setSourceId(null);
        n.setReadStatus(0);
        n.setCreatedAt(java.time.LocalDateTime.now());
        notificationMapper.insert(n);
    }

    private String buildContentJson(Long actorId, Long postId, String postTitle, String commentContent) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"actorId\":\"").append(actorId).append("\"");
        if (postId != null) {
            sb.append(",\"postId\":\"").append(postId).append("\"");
        }
        if (postTitle != null) {
            sb.append(",\"postTitle\":\"").append(escapeJson(truncate(postTitle, 40))).append("\"");
        }
        if (commentContent != null) {
            sb.append(",\"commentContent\":\"").append(escapeJson(commentContent)).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
