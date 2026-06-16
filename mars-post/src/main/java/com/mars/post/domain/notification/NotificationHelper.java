package com.mars.post.domain.notification;

import com.mars.post.domain.post.Post;
import com.mars.post.domain.post.PostMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import com.mars.common.model.Notification;

@Component
public class NotificationHelper {

    @Autowired
    private InteractionNotificationMapper notificationMapper;

    @Autowired
    private PostMapper postMapper;

    /**
     * 创建点赞通知
     * @param actorId 点赞者ID
     * @param actorName 点赞者用户名
     * @param postId 帖子ID
     */
    public void notifyLike(Long actorId, String actorName, Long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null || post.getUserId().equals(actorId)) {
            return; // 帖子不存在或自己给自己点赞，不通知
        }

        Notification n = new Notification();
        n.setUserId(post.getUserId());
        n.setCategory("interaction");
        n.setTitle(truncate(actorName, 128));
        n.setContent(buildContentJson(actorId, postId, post.getTitle(), null));
        n.setSourceType("like");
        n.setSourceId(String.valueOf(postId));
        n.setReadStatus(0);
        n.setCreatedAt(LocalDateTime.now());
        notificationMapper.insert(n);
    }

    /**
     * 创建评论通知
     * @param actorId 评论者ID
     * @param actorName 评论者用户名
     * @param postId 帖子ID
     * @param commentContent 评论内容
     */
    public void notifyComment(Long actorId, String actorName, Long postId, String commentContent) {
        Post post = postMapper.selectById(postId);
        if (post == null || post.getUserId().equals(actorId)) {
            return; // 帖子不存在或自己评论自己，不通知
        }

        Notification n = new Notification();
        n.setUserId(post.getUserId());
        n.setCategory("interaction");
        n.setTitle(truncate(actorName, 128));
        n.setContent(buildContentJson(actorId, postId, post.getTitle(), truncate(commentContent, 60)));
        n.setSourceType("comment");
        n.setSourceId(String.valueOf(postId));
        n.setReadStatus(0);
        n.setCreatedAt(LocalDateTime.now());
        notificationMapper.insert(n);
    }

    /**
     * 创建关注通知
     * @param followerId 关注者ID
     * @param followerName 关注者用户名
     * @param followedId 被关注者ID
     */
    public void notifyFollow(Long followerId, String followerName, Long followedId) {
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
        n.setCreatedAt(LocalDateTime.now());
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
