package com.mars.common.outbox;

import com.mars.common.cache.CacheService;
import com.mars.common.mq.MqTopics;
import com.mars.common.trace.TraceContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 发件箱消息轮询发布器
 * 定时扫描 outbox_message 表，将待发送消息投递到 RocketMQ
 *
 * 特性：
 * - 分布式锁防止多实例重复消费
 * - 指数退避重试（10s, 30s, 90s, 270s, 810s）
 * - 超过最大重试次数发送到死信队列
 * - 自动清理 7 天前的已发送记录
 */
@Slf4j
@Component
@ConditionalOnClass(com.baomidou.mybatisplus.core.mapper.BaseMapper.class)
public class OutboxPublisher {

    private static final int BATCH_SIZE = 100;
    private static final String LOCK_KEY = "lock:outbox-publisher";
    private static final Duration LOCK_TTL = Duration.ofSeconds(55);

    @Autowired(required = false)
    private RocketMQTemplate rocketMQTemplate;

    @Autowired(required = false)
    private OutboxMessageMapper outboxMapper;

    @Autowired
    private CacheService cacheService;

    @Autowired(required = false)
    private TraceContext traceContext;

    @Scheduled(fixedDelay = 2000)
    public void publish() {
        if (outboxMapper == null) {
            return;
        }
        // 分布式锁：多实例部署时只有一个实例执行轮询
        String lockOwner = cacheService.tryLock(LOCK_KEY, LOCK_TTL);
        if (lockOwner == null) {
            return;
        }
        try {
            List<OutboxMessage> pending = outboxMapper.findPending(BATCH_SIZE);
            if (pending.isEmpty()) {
                return;
            }
            log.info("发件箱扫描: 待发送 {} 条", pending.size());
            for (OutboxMessage msg : pending) {
                try {
                    sendToMq(msg);
                    markSent(msg);
                } catch (Exception e) {
                    handleFailure(msg, e);
                }
            }
        } finally {
            cacheService.unlock(LOCK_KEY, lockOwner);
        }
    }

    private void sendToMq(OutboxMessage msg) {
        if (rocketMQTemplate == null) {
            // 开发模式（未配置 RocketMQ）：直接标记为已发送
            log.debug("RocketMQ 未配置，跳过发送: bizKey={}", msg.getBizKey());
            return;
        }
        String destination = msg.getTag() != null && !msg.getTag().isEmpty()
                ? msg.getTopic() + ":" + msg.getTag()
                : msg.getTopic();

        // 注入 traceId 到 payload 以便消费端关联链路
        String payload = injectTraceId(msg.getPayload());

        rocketMQTemplate.convertAndSend(destination, payload);
        log.debug("发件箱消息已发送: bizKey={}, topic={}", msg.getBizKey(), destination);
    }

    private String injectTraceId(String payload) {
        if (traceContext == null) {
            return payload;
        }
        String traceId = traceContext.currentTraceId();
        if (traceId == null || "unknown".equals(traceId)) {
            return payload;
        }
        // 在 JSON 末尾注入 _traceId 字段
        if (payload.endsWith("}")) {
            return payload.substring(0, payload.length() - 1)
                    + ",\"_traceId\":\"" + traceId + "\"}";
        }
        return payload;
    }

    private void markSent(OutboxMessage msg) {
        msg.setStatus(1); // SENT
        msg.setUpdatedAt(LocalDateTime.now());
        outboxMapper.updateById(msg);
    }

    private void handleFailure(OutboxMessage msg, Exception e) {
        msg.setRetryCount(msg.getRetryCount() + 1);
        msg.setUpdatedAt(LocalDateTime.now());

        if (msg.getRetryCount() >= msg.getMaxRetry()) {
            msg.setStatus(2); // FAILED（最终失败）
            log.error("发件箱消息达到最大重试次数，转入死信: bizKey={}, retryCount={}",
                    msg.getBizKey(), msg.getRetryCount(), e);
            sendToDeadLetter(msg);
        } else {
            // 指数退避：10s, 30s, 90s, 270s
            long delaySeconds = (long) (10 * Math.pow(3, msg.getRetryCount()));
            msg.setNextRetryAt(LocalDateTime.now().plusSeconds(delaySeconds));
            log.warn("发件箱消息发送失败，将重试: bizKey={}, retryCount={}, nextRetry={}s",
                    msg.getBizKey(), msg.getRetryCount(), delaySeconds);
        }
        outboxMapper.updateById(msg);
    }

    private void sendToDeadLetter(OutboxMessage msg) {
        if (rocketMQTemplate == null) {
            return;
        }
        try {
            String dlPayload = String.format(
                    "{\"originalTopic\":\"%s\",\"originalBizKey\":\"%s\",\"payload\":%s,\"retryCount\":%d,\"failedAt\":\"%s\"}",
                    msg.getTopic(), msg.getBizKey(), msg.getPayload(), msg.getRetryCount(), LocalDateTime.now());
            rocketMQTemplate.convertAndSend(MqTopics.DEAD_LETTER, dlPayload);
        } catch (Exception dlEx) {
            log.error("死信队列发送也失败: bizKey={}", msg.getBizKey(), dlEx);
        }
    }

    /**
     * 每天凌晨 3 点清理 7 天前的已发送记录
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanup() {
        if (outboxMapper == null) {
            return;
        }
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
            int deleted = outboxMapper.delete(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OutboxMessage>()
                            .eq(OutboxMessage::getStatus, 1)
                            .lt(OutboxMessage::getUpdatedAt, cutoff));
            if (deleted > 0) {
                log.info("发件箱清理: 删除 {} 条已发送记录", deleted);
            }
        } catch (Exception e) {
            log.warn("发件箱清理失败: {}", e.getMessage());
        }
    }
}
