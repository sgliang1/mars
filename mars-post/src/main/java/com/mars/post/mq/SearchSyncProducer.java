package com.mars.post.mq;

import com.mars.common.mq.MqTopics;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 搜索同步消息生产者
 * 当帖子发布、更新、删除时，发送消息触发 ES 索引同步
 */
@Component
public class SearchSyncProducer {

    private static final Logger log = LoggerFactory.getLogger(SearchSyncProducer.class);

    @Autowired(required = false)
    private RocketMQTemplate rocketMQTemplate;

    /**
     * 发送帖子同步消息
     * @param postId  帖子 ID
     * @param action  操作类型: INDEX / DELETE
     */
    public void sendPostSync(Long postId, String action) {
        if (rocketMQTemplate == null) {
            log.debug("RocketMQ 未配置，跳过搜索同步: postId={}, action={}", postId, action);
            return;
        }
        try {
            String payload = "{\"postId\":" + postId + ",\"action\":\"" + action + "\"}";
            rocketMQTemplate.convertAndSend(
                    MqTopics.SEARCH_SYNC + ":" + MqTopics.TAG_POST,
                    payload);
            log.debug("搜索同步消息已发送: postId={}, action={}", postId, action);
        } catch (Exception e) {
            log.error("发送搜索同步消息失败: postId={}, error={}", postId, e.getMessage());
        }
    }
}