package com.mars.interaction.mq;

import com.mars.common.mq.MqTopics;
import com.mars.common.mq.NotificationMessage;
import com.mars.common.model.Notification;
import com.mars.common.push.PushPayload;
import com.mars.common.push.PushPreferenceHelper;
import com.mars.common.push.PushService;
import com.mars.interaction.domain.notification.InteractionNotificationMapper;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 通知消息消费者
 * 消费 MQ 中的互动通知，写入 notification 表，并对离线用户发送推送
 */
@Component
@RocketMQMessageListener(
        topic = MqTopics.NOTIFICATION,
        selectorExpression = MqTopics.TAG_INTERACTION,
        consumerGroup = "mars-notification-consumer"
)
public class NotificationConsumer implements RocketMQListener<NotificationMessage> {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    @Autowired
    private InteractionNotificationMapper notificationMapper;

    @Autowired
    private PushService pushService;

    @Autowired
    private PushPreferenceHelper preferenceHelper;

    @Override
    public void onMessage(NotificationMessage msg) {
        try {
            Notification n = new Notification();
            n.setUserId(msg.getUserId());
            n.setCategory(msg.getCategory());
            n.setTitle(msg.getTitle());
            n.setContent(msg.getContent());
            n.setSourceType(msg.getSourceType());
            n.setSourceId(msg.getSourceId());
            n.setActorId(msg.getActorId());
            n.setPostId(msg.getPostId());
            n.setReadStatus(0);
            n.setCreatedAt(msg.getCreatedAt() != null ? msg.getCreatedAt() : java.time.LocalDateTime.now());
            notificationMapper.insert(n);
            log.debug("通知已写入 DB: userId={}, sourceType={}", msg.getUserId(), msg.getSourceType());

            sendPushIfOffline(msg);
        } catch (Exception e) {
            log.error("消费通知消息失败: userId={}, error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    private void sendPushIfOffline(NotificationMessage msg) {
        try {
            if (!preferenceHelper.shouldPushInteraction(msg.getUserId())) {
                return;
            }

            String title = buildPushTitle(msg);
            String body = buildPushBody(msg);

            PushPayload payload = PushPayload.of(title, body, "interaction")
                    .withClickAction("/notifications");

            pushService.sendToUser(msg.getUserId(), payload);
        } catch (Exception e) {
            log.warn("发送互动推送失败: userId={}, error={}", msg.getUserId(), e.getMessage());
        }
    }

    private String buildPushTitle(NotificationMessage msg) {
        return switch (msg.getSourceType()) {
            case "like" -> "收到新点赞";
            case "comment" -> "收到新评论";
            case "follow" -> "新增关注";
            case "mention_post", "mention_comment" -> "有人提到了你";
            default -> "新通知";
        };
    }

    private String buildPushBody(NotificationMessage msg) {
        String title = msg.getTitle() != null ? msg.getTitle() : "";
        return switch (msg.getSourceType()) {
            case "like" -> title + " 赞了你的帖子";
            case "comment" -> title + " 评论了你的帖子";
            case "follow" -> title + " 关注了你";
            case "mention_post", "mention_comment" -> title + " 在帖子中提到了你";
            default -> "点击查看";
        };
    }
}
