package com.mars.interaction.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mars.common.mq.MqTopics;
import com.mars.common.mq.NotificationMessage;
import com.mars.common.outbox.OutboxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 通知消息生产者
 * 将互动通知（点赞/评论/关注）写入发件箱，由 OutboxPublisher 异步投递
 */
@Slf4j
@Component
public class NotificationProducer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private OutboxService outboxService;

    public void sendInteraction(NotificationMessage message) {
        try {
            String payload = MAPPER.writeValueAsString(message);
            String bizKey = message.getBizKey() != null ? message.getBizKey()
                    : "notification:" + message.getSourceType() + ":" + message.getActorId()
                    + ":" + message.getUserId() + ":" + message.getSourceId();
            outboxService.save(MqTopics.NOTIFICATION, MqTopics.TAG_INTERACTION, payload, bizKey);
            log.debug("通知消息已写入发件箱: userId={}, sourceType={}", message.getUserId(), message.getSourceType());
        } catch (Exception e) {
            log.error("序列化通知消息失败: userId={}, sourceType={}, error={}",
                    message.getUserId(), message.getSourceType(), e.getMessage());
        }
    }
}
