package com.mars.chat.infrastructure.config;

import com.mars.chat.infrastructure.websocket.RedisPubSubListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisPubSubConfig {

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisPubSubListener redisPubSubListener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        // 直接使用 MessageListener 实现，不用 MessageListenerAdapter 包装
        container.addMessageListener(redisPubSubListener, new ChannelTopic(RedisPubSubListener.BROADCAST_CHANNEL));
        return container;
    }
}