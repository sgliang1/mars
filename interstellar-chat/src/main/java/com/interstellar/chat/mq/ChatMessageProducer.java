package com.interstellar.chat.mq;

import com.interstellar.common.mq.MqTopics;
import com.interstellar.common.outbox.OutboxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 聊天消息生产者
 * 将离线推送消息写入发件箱，由 OutboxPublisher 异步投递到 RocketMQ
 */
@Slf4j
@Component
public class ChatMessageProducer {

    @Autowired
    private OutboxService outboxService;

    /**
     * 发送离线消息通知
     * 当接收方不在线时，将消息推入离线队列
     */
    public void sendOfflinePush(Long receiverId, Long conversationId, Long messageId, String senderName, String preview) {
        String payload = String.format(
                "{\"receiverId\":%d,\"conversationId\":%d,\"messageId\":%d,\"senderName\":\"%s\",\"preview\":\"%s\"}",
                receiverId, conversationId, messageId, escapeJson(senderName), escapeJson(truncate(preview, 60)));
        String bizKey = "chat:offline:" + receiverId + ":" + messageId;
        outboxService.save(MqTopics.CHAT_MESSAGE, MqTopics.TAG_OFFLINE, payload, bizKey);
        log.debug("离线推送消息已写入发件箱: receiverId={}, messageId={}", receiverId, messageId);
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
