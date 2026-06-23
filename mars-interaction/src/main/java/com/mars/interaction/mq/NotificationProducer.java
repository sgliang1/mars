package com.mars.interaction.mq;

import com.mars.common.mq.MqTopics;
import com.mars.common.mq.NotificationMessage;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 通知消息生产者
 * 将互动通知（点赞/评论/关注）发送到 MQ，由消费者异步写入 DB
 */
@Component
public class NotificationProducer {

    private static final Logger log = LoggerFactory.getLogger(NotificationProducer.class);

    @Autowired(required = false)
    private RocketMQTemplate rocketMQTemplate;

    public void sendInteraction(NotificationMessage message) {
        if (rocketMQTemplate == null) {
            log.warn("RocketMQTemplate 未配置，跳过通知消息发送");
            return;
        }
        try {
            rocketMQTemplate.convertAndSend(
                    MqTopics.NOTIFICATION + ":" + MqTopics.TAG_INTERACTION,
                    message);
            log.debug("通知消息已发送: userId={}, sourceType={}", message.getUserId(), message.getSourceType());
        } catch (Exception e) {
            log.error("发送通知消息失败: userId={}, sourceType={}, error={}",
                    message.getUserId(), message.getSourceType(), e.getMessage());
        }
    }
}
