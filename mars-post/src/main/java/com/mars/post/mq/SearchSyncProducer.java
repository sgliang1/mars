package com.mars.post.mq;

import com.mars.common.mq.MqTopics;
import com.mars.common.outbox.OutboxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 搜索同步消息生产者
 * 当帖子发布、更新、删除时，写入发件箱，由 OutboxPublisher 异步投递到 RocketMQ
 */
@Slf4j
@Component
public class SearchSyncProducer {

    @Autowired
    private OutboxService outboxService;

    /**
     * 发送帖子同步消息
     * @param postId  帖子 ID
     * @param action  操作类型: INDEX / DELETE
     */
    public void sendPostSync(Long postId, String action) {
        String payload = "{\"postId\":" + postId + ",\"action\":\"" + action + "\"}";
        String bizKey = "search:post:" + postId + ":" + action;
        outboxService.save(MqTopics.SEARCH_SYNC, MqTopics.TAG_POST, payload, bizKey);
        log.debug("搜索同步消息已写入发件箱: postId={}, action={}", postId, action);
    }
}
