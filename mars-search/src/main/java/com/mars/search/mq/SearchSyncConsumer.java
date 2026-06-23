package com.mars.search.mq;

import com.mars.common.mq.MqTopics;
import com.mars.search.domain.search.SearchSyncService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 搜索同步消息消费者
 * 消费 mars-post 发出的帖子变更消息，同步到 Elasticsearch
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = MqTopics.SEARCH_SYNC,
        selectorExpression = MqTopics.TAG_POST,
        consumerGroup = "mars-search-sync-consumer"
)
public class SearchSyncConsumer implements RocketMQListener<String> {

    @Autowired
    private SearchSyncService searchSyncService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void onMessage(String payload) {
        try {
            log.debug("收到搜索同步消息: {}", payload);
            JsonNode node = objectMapper.readTree(payload);
            Long postId = node.get("postId").asLong();
            String action = node.get("action").asText();

            if ("INDEX".equals(action)) {
                searchSyncService.syncPost(postId);
            } else if ("DELETE".equals(action)) {
                searchSyncService.deletePostIndex(postId);
            } else {
                log.warn("未知的搜索同步操作: {}", action);
            }
        } catch (Exception e) {
            log.error("处理搜索同步消息失败: payload={}, error={}", payload, e.getMessage());
        }
    }
}
