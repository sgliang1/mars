package com.interstellar.search.mq;

import com.interstellar.common.cache.CacheService;
import com.interstellar.common.mq.MqTopics;
import com.interstellar.search.domain.search.SearchSyncService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 搜索同步消息消费者
 * 消费 interstellar-post 发出的帖子变更消息，同步到 Elasticsearch
 * 仅在 ES 启用时激活
 * 支持幂等：相同 postId+action 的消息只处理一次
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "search.elasticsearch.enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = MqTopics.SEARCH_SYNC,
        selectorExpression = MqTopics.TAG_POST,
        consumerGroup = "interstellar-search-sync-consumer"
)
public class SearchSyncConsumer implements RocketMQListener<String> {

    private static final String IDEMPOTENCY_PREFIX = "interstellar:search:synced:";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(1);

    @Autowired
    private SearchSyncService searchSyncService;

    @Autowired
    private CacheService cacheService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void onMessage(String payload) {
        try {
            log.debug("收到搜索同步消息: {}", payload);
            JsonNode node = objectMapper.readTree(payload);
            Long postId = node.get("postId").asLong();
            String action = node.get("action").asText();

            // 幂等检查：同一帖子同一操作只处理一次
            String idempotencyKey = IDEMPOTENCY_PREFIX + postId + ":" + action;
            Boolean isNew = cacheService.getRedisTemplate().opsForValue()
                    .setIfAbsent(idempotencyKey, "1", IDEMPOTENCY_TTL);
            if (!Boolean.TRUE.equals(isNew)) {
                log.debug("搜索同步消息已处理，跳过: postId={}, action={}", postId, action);
                return;
            }

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
