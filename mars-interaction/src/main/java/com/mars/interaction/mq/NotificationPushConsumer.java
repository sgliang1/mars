package com.mars.interaction.mq;

import com.mars.common.cache.CacheService;
import com.mars.common.mq.MqTopics;
import com.mars.common.mq.NotificationMessage;
import com.mars.common.push.NotificationAggregator;
import com.mars.common.push.PushPayload;
import com.mars.common.push.PushPreferenceHelper;
import com.mars.common.push.PushService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 互动通知推送消费者
 * 与 NotificationConsumer (写 DB) 并行消费同一 Topic，各自独立消费组
 * 支持幂等：同一推送消息只发送一次
 */
@Component
@RocketMQMessageListener(
        topic = MqTopics.NOTIFICATION,
        selectorExpression = MqTopics.TAG_INTERACTION,
        consumerGroup = "mars-notification-push-consumer"
)
public class NotificationPushConsumer implements RocketMQListener<NotificationMessage> {

    private static final Logger log = LoggerFactory.getLogger(NotificationPushConsumer.class);
    private static final String IDEMPOTENCY_PREFIX = "mars:push:processed:";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    @Autowired
    private PushService pushService;

    @Autowired
    private PushPreferenceHelper preferenceHelper;

    @Autowired
    private NotificationAggregator aggregator;

    @Autowired
    private CacheService cacheService;

    @Override
    public void onMessage(NotificationMessage msg) {
        try {
            Long userId = msg.getUserId();
            if (userId == null) return;

            // 幂等检查
            String idempotencyKey = IDEMPOTENCY_PREFIX
                    + msg.getSourceType() + ":" + msg.getActorId()
                    + ":" + msg.getUserId() + ":" + msg.getSourceId();
            Boolean isNew = cacheService.getRedisTemplate().opsForValue()
                    .setIfAbsent(idempotencyKey, "1", IDEMPOTENCY_TTL);
            if (!Boolean.TRUE.equals(isNew)) {
                log.debug("推送消息已处理，跳过: {}", idempotencyKey);
                return;
            }

            if (!preferenceHelper.shouldPushInteraction(userId)) {
                return;
            }

            String mergedText = aggregator.tryAggregate(
                    userId,
                    msg.getSourceType() != null ? msg.getSourceType() : "interaction",
                    msg.getTitle(),
                    msg.getSourceType());

            if (mergedText == null) {
                return;
            }

            String clickAction = buildClickAction(msg);
            PushPayload payload = PushPayload.of("新通知", mergedText, "interaction")
                    .withClickAction(clickAction)
                    .withData("sourceType", msg.getSourceType() != null ? msg.getSourceType() : "")
                    .withData("sourceId", msg.getSourceId() != null ? msg.getSourceId() : "");

            pushService.sendToUser(userId, payload);
            log.info("互动推送已发送: userId={}, text={}", userId, mergedText);

        } catch (Exception e) {
            log.error("互动通知推送处理失败: {}", e.getMessage(), e);
        }
    }

    private String buildClickAction(NotificationMessage msg) {
        if (msg.getSourceId() == null) return "/message";
        return switch (msg.getSourceType() != null ? msg.getSourceType() : "") {
            case "like", "comment" -> "/detail/" + msg.getSourceId();
            case "follow" -> "/author/" + msg.getSourceId();
            default -> "/message";
        };
    }
}
