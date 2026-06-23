package com.mars.chat.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mars.chat.infrastructure.websocket.WebSocketSessionManager;
import com.mars.common.mq.MqTopics;
import com.mars.common.push.NotificationAggregator;
import com.mars.common.push.PushPayload;
import com.mars.common.push.PushPreferenceHelper;
import com.mars.common.push.PushService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 离线消息消费者
 * 收到离线消息 → 检查在线状态 → 检查推送偏好 → 调用 PushService 推送
 */
@Component
@RocketMQMessageListener(
        topic = MqTopics.CHAT_MESSAGE,
        selectorExpression = MqTopics.TAG_OFFLINE,
        consumerGroup = "mars-chat-offline-consumer"
)
public class OfflineMessageConsumer implements RocketMQListener<String> {

    private static final Logger log = LoggerFactory.getLogger(OfflineMessageConsumer.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private PushService pushService;

    @Autowired
    private PushPreferenceHelper preferenceHelper;

    @Autowired(required = false)
    private WebSocketSessionManager sessionManager;

    @Override
    public void onMessage(String payload) {
        try {
            Map<String, Object> msg = MAPPER.readValue(payload, Map.class);
            Long receiverId = toLong(msg.get("receiverId"));
            String senderName = (String) msg.getOrDefault("senderName", "某人");
            String preview = (String) msg.getOrDefault("preview", "");

            if (receiverId == null) return;

            // 1. 用户在线则跳过（WebSocket 会实时推送）
            if (sessionManager != null && sessionManager.isOnline(receiverId)) {
                log.debug("用户 {} 在线，跳过离线推送", receiverId);
                return;
            }

            // 2. 检查推送偏好
            if (!preferenceHelper.shouldPushChat(receiverId)) {
                log.debug("用户 {} 免打扰或聊天推送关闭，仅记录通知表", receiverId);
                return;
            }

            // 3. 发送推送
            String body = preview.isEmpty()
                    ? senderName + " 发来一条消息"
                    : senderName + ": " + preview;

            PushPayload pushPayload = PushPayload.of("新消息", body, "chat")
                    .withClickAction("/channels/public");

            pushService.sendToUser(receiverId, pushPayload);
            log.info("离线推送已发送: receiverId={}, sender={}", receiverId, senderName);

        } catch (Exception e) {
            log.error("离线消息处理失败: {}", e.getMessage(), e);
        }
    }

    private Long toLong(Object val) {
        if (val == null) return null;
        if (val instanceof Number n) return n.longValue();
        try { return Long.parseLong(val.toString()); } catch (Exception e) { return null; }
    }
}