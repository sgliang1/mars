package com.interstellar.chat.infrastructure.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Redis Pub/Sub 监听器：接收跨实例 WebSocket 广播消息并转发给本地连接
 * 使用 StringRedisTemplate 发送/接收 JSON 字符串，避免序列化方式不一致
 */
@Slf4j
@Component
public class RedisPubSubListener implements MessageListener {

    public static final String BROADCAST_CHANNEL = "interstellar:ws:broadcast";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Autowired
    private ChatWebSocketHandler chatWebSocketHandler;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
            if (!BROADCAST_CHANNEL.equals(channel)) return;

            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            BroadcastMessage broadcast = OBJECT_MAPPER.readValue(json, BroadcastMessage.class);
            chatWebSocketHandler.handleBroadcast(broadcast);
        } catch (Exception e) {
            log.error("处理 Redis 广播消息失败", e);
        }
    }

    /**
     * 发布广播消息到 Redis（JSON 字符串序列化，与 onMessage 对称）
     */
    public void publish(BroadcastMessage broadcast) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(broadcast);
            stringRedisTemplate.convertAndSend(BROADCAST_CHANNEL, json);
        } catch (Exception e) {
            log.error("发布 Redis 广播消息失败", e);
        }
    }

    /**
     * 广播消息载体
     */
    @lombok.Data
    public static class BroadcastMessage {
        /** 广播类型: public / conversation */
        private String type;
        /** 会话ID（conversation 类型必填） */
        private Long conversationId;
        /** 发送者ID（广播时跳过） */
        private Long senderId;
        /** 消息JSON（已序列化） */
        private String messageJson;
        /** 离线推送信息 */
        private Long messageId;
        private String senderName;
        private String content;
    }
}