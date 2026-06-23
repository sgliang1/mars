package com.mars.post.domain.post;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Feed 缓存失效监听器
 * 订阅 Redis Pub/Sub 频道 mars:event:feed:evict
 * 当 mars-interaction 中发生评论/点赞等影响热帖排序的操作时，清除热帖缓存
 */
@Component
public class FeedEvictionListener implements MessageListener {

    @Autowired
    private FeedService feedService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String channel = new String(message.getChannel());
            if (!"mars:event:feed:evict".equals(channel)) {
                return;
            }
            String body = new String(message.getBody());
            if ("hot".equals(body)) {
                feedService.evictHotFeedCache();
            }
        } catch (Exception ignored) {}
    }
}
