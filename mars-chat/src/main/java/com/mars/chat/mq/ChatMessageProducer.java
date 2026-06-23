package com.mars.chat.mq;

import com.mars.common.mq.MqTopics;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 聊天消息生产者
 * 发送消息到 MQ，用于离线推送和异步处理
 */
@Component
public class ChatMessageProducer {

    private static final Logger log = LoggerFactory.getLogger(ChatMessageProducer.class);

    @Autowired(required = false)
    private RocketMQTemplate rocketMQTemplate;

    /**
     * 发送离线消息通知
     * 当接收方不在线时，将消息推入离线队列
     */
    public void sendOfflinePush(Long receiverId, Long conversationId, Long messageId, String senderName, String preview) {
        if (rocketMQTemplate == null) {
            log.debug("RocketMQ 未配置，跳过离线推送: receiverId={}, messageId={}", receiverId, messageId);
            return;
        }
        try {
            String payload = String.format(
                    "{\"receiverId\":%d,\"conversationId\":%d,\"messageId\":%d,\"senderName\":\"%s\",\"preview\":\"%s\"}",
                    receiverId, conversationId, messageId, escapeJson(senderName), escapeJson(truncate(preview, 60)));
            rocketMQTemplate.convertAndSend(
                    MqTopics.CHAT_MESSAGE + ":" + MqTopics.TAG_OFFLINE,
                    payload);
            log.debug("离线推送消息已发送: receiverId={}, messageId={}", receiverId, messageId);
        } catch (Exception e) {
            log.error("发送离线推送失败: receiverId={}, messageId={}, error={}", receiverId, messageId, e.getMessage());
        }
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