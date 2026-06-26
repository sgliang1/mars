package com.interstellar.post.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interstellar.common.mq.MqTopics;
import com.interstellar.common.mq.NotificationMessage;
import com.interstellar.common.outbox.OutboxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 通知消息生产者（轻量版）
 * 由 PostService 在点赞时发送通知事件到 interstellar-interaction 消费
 * 通过 OutboxService 保证事务一致性
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
