package com.mars.post.infrastructure.config;

import com.mars.post.domain.post.FeedEvictionListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Redis Pub/Sub 监听器配置
 * 注册 Feed 缓存失效事件监听
 */
@Configuration
public class RedisListenerConfig {

    @Bean
    public RedisMessageListenerContainer feedEvictionContainer(
            RedisConnectionFactory factory,
            FeedEvictionListener feedEvictionListener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener(feedEvictionListener, new ChannelTopic("mars:event:feed:evict"));
        return container;
    }
}
