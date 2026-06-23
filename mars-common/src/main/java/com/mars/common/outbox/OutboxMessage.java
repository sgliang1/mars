package com.mars.common.outbox;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 事务性发件箱消息实体
 * 业务操作和 outbox 写入在同一事务内完成，保证原子性
 * OutboxPublisher 异步轮询发送到 RocketMQ
 */
@Data
@TableName("outbox_message")
public class OutboxMessage {

    /** UUID，兼做幂等键 */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** RocketMQ Topic */
    private String topic;

    /** RocketMQ Tag */
    private String tag;

    /** 消息体 JSON */
    private String payload;

    /** 业务幂等键，如 search:post:123:INDEX */
    private String bizKey;

    /** 0=PENDING, 1=SENT, 2=FAILED */
    private Integer status;

    /** 已重试次数 */
    private Integer retryCount;

    /** 最大重试次数 */
    private Integer maxRetry;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** 下次重试时间（指数退避） */
    private LocalDateTime nextRetryAt;
}
