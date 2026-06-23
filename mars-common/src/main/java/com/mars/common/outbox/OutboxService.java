package com.mars.common.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 事务性发件箱服务
 * 核心方法 save() 参与调用方的事务，保证业务数据和 outbox 记录原子写入
 *
 * 使用方式：在 @Transactional 的业务方法中调用 outboxService.save(...)
 *           当前事务提交后，OutboxPublisher 会异步轮询并发送到 RocketMQ
 */
@Slf4j
@Service
@ConditionalOnClass(com.baomidou.mybatisplus.core.mapper.BaseMapper.class)
public class OutboxService {

    @Autowired(required = false)
    private OutboxMessageMapper outboxMapper;

    /**
     * 保存一条待发送的 MQ 消息到 outbox 表
     * 此方法加入调用方的事务，保证原子性
     *
     * @param topic   RocketMQ Topic
     * @param tag     RocketMQ Tag（可为 null）
     * @param payload 消息体 JSON
     * @param bizKey  业务幂等键，用于消费端去重
     */
    @Transactional
    public void save(String topic, String tag, String payload, String bizKey) {
        if (outboxMapper == null) {
            log.warn("OutboxMessageMapper 未注册，跳过发件箱写入: bizKey={}", bizKey);
            return;
        }
        OutboxMessage msg = new OutboxMessage();
        msg.setTopic(topic);
        msg.setTag(tag);
        msg.setPayload(payload);
        msg.setBizKey(bizKey);
        msg.setStatus(0); // PENDING
        msg.setRetryCount(0);
        msg.setMaxRetry(5);
        msg.setCreatedAt(LocalDateTime.now());
        msg.setUpdatedAt(LocalDateTime.now());
        outboxMapper.insert(msg);
        log.debug("消息已写入发件箱: bizKey={}", bizKey);
    }
}
