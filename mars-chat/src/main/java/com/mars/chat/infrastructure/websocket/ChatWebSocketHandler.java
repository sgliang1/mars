package com.mars.chat.infrastructure.websocket;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mars.chat.domain.conversation.Conversation;
import com.mars.chat.domain.conversation.ConversationMember;
import com.mars.chat.domain.conversation.ConversationMemberMapper;
import com.mars.chat.domain.conversation.ConversationMapper;
import com.mars.chat.domain.message.ChatMessage;
import com.mars.chat.domain.message.ChatMessageMapper;
import com.mars.chat.domain.message.ConversationMessage;
import com.mars.chat.domain.message.ConversationMessageMapper;
import com.mars.chat.domain.message.SensitiveFilter;
import com.mars.chat.mq.ChatMessageProducer;
import com.mars.common.Result;
import com.mars.common.cache.CacheKeys;
import com.mars.common.cache.CacheService;
import com.mars.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final String PUBLIC_CHANNEL_BIZ_KEY = "public-lobby";
    private static final int MAX_CONNECTIONS_PER_USER = 5;
    private static final int MAX_GLOBAL_CONNECTIONS = 10000;
    private static final String SERVER_INSTANCE_ID;
    static {
        String hostId = "unknown";
        try { hostId = InetAddress.getLocalHost().getHostName(); } catch (Exception ignored) {}
        SERVER_INSTANCE_ID = hostId + ":" + ProcessHandle.current().pid();
    }

    private static final ConcurrentHashMap<Long, WebSocketSession> ONLINE_USERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Set<Long>> USER_CONVERSATIONS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Set<Long>> CONVERSATION_MEMBERS = new ConcurrentHashMap<>();

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final ScheduledExecutorService HEARTBEAT_EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ws-heartbeat");
        t.setDaemon(true);
        return t;
    });

    @Autowired
    private ConversationMapper conversationMapper;

    @Autowired
    private ConversationMemberMapper conversationMemberMapper;

    @Autowired
    private ConversationMessageMapper conversationMessageMapper;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private SensitiveFilter sensitiveFilter;

    @Autowired
    private WebSocketSessionManager sessionManager;

    @Autowired
    private CacheService cacheService;

    @Lazy
    @Autowired
    private RedisPubSubListener redisPubSubListener;

    @Autowired(required = false)
    private ChatMessageProducer chatMessageProducer;

    private boolean heartbeatStarted = false;

    private synchronized void ensureHeartbeatStarted() {
        if (heartbeatStarted) return;
        heartbeatStarted = true;

        HEARTBEAT_EXECUTOR.scheduleAtFixedRate(() -> {
            try {
                List<Long> staleUserIds = new ArrayList<>();
                ONLINE_USERS.forEach((userId, session) -> {
                    if (!session.isOpen()) {
                        staleUserIds.add(userId);
                    }
                });
                staleUserIds.forEach(userId -> {
                    removeUser(userId);
                    sessionManager.userOffline(userId);
                });

                ONLINE_USERS.forEach((userId, session) -> {
                    if (session.isOpen()) {
                        sessionManager.heartbeat(userId);
                    }
                });
            } catch (Exception e) {
                log.warn("Heartbeat cleanup error", e);
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        ensureHeartbeatStarted();

        // 全局连接数上限
        if (ONLINE_USERS.size() >= MAX_GLOBAL_CONNECTIONS) {
            session.close(CloseStatus.SERVICE_OVERLOAD.withReason("Server connection limit reached"));
            return;
        }

        Long userId = null;
        String username = null;

        String headerUserId = session.getHandshakeHeaders().getFirst("X-User-Id");
        String headerUsername = session.getHandshakeHeaders().getFirst("X-User-Name");

        if (headerUserId != null && !headerUserId.isEmpty()) {
            try {
                userId = Long.parseLong(headerUserId);
                username = headerUsername != null ? java.net.URLDecoder.decode(headerUsername, "UTF-8") : "user";
            } catch (Exception e) {
                log.warn("Header 认证解析失败: {}", e.getMessage());
            }
        }

        if (userId == null) {
            String query = session.getUri() == null ? "" : session.getUri().getQuery();
            String token = null;
            if (query != null) {
                Map<String, String> params = UriComponentsBuilder.newInstance()
                        .query(query).build().getQueryParams().toSingleValueMap();
                token = params.get("token");
            }

            if (token == null || token.isEmpty()) {
                session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Unauthorized: missing token"));
                return;
            }

            try {
                Claims claims = JwtUtil.parseToken(token);
                userId = Long.parseLong(claims.get("userId").toString());
                username = claims.get("username").toString();
            } catch (Exception e) {
                log.error("WebSocket auth failed: {}", e.getMessage());
                session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Invalid or expired token"));
                return;
            }
        }

        session.getAttributes().put("userId", userId);
        session.getAttributes().put("username", username);

        // 单用户连接数上限：关闭最早的连接
        WebSocketSession oldSession = ONLINE_USERS.put(userId, session);
        if (oldSession != null && oldSession.isOpen()) {
            try { oldSession.close(CloseStatus.NORMAL); } catch (IOException e) { log.warn("Failed to close old session", e); }
        }

        sessionManager.userOnline(userId, SERVER_INSTANCE_ID);
        loadUserConversations(userId);
        pushUnacknowledgedMessages(userId, session);

        log.info("User [{} - {}] connected, online count: {}", userId, username, ONLINE_USERS.size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) {
        Long userId = (Long) session.getAttributes().get("userId");
        String username = (String) session.getAttributes().get("username");
        if (userId == null) return;

        try {
            Map<String, Object> msgMap = OBJECT_MAPPER.readValue(textMessage.getPayload(), Map.class);
            String type = (String) msgMap.get("type");

            if ("ack".equals(type)) {
                handleAck(userId, msgMap);
                return;
            }

            if ("ping".equals(type)) {
                sessionManager.heartbeat(userId);
                sendJson(session, Map.of("type", "pong", "timestamp", System.currentTimeMillis()));
                return;
            }

            String rawContent = (String) msgMap.get("content");
            if (rawContent == null || rawContent.trim().isEmpty()) return;

            String cleanContent = sensitiveFilter.filter(rawContent);
            String convIdStr = (String) msgMap.get("conversationId");
            String tempId = (String) msgMap.get("tempId");

            int messageType = 0;
            Object typeObj = msgMap.get("messageType");
            if (typeObj instanceof Number n) {
                messageType = n.intValue();
            } else if (typeObj != null) {
                messageType = Integer.parseInt(typeObj.toString());
            }

            if (convIdStr != null && !convIdStr.isBlank()) {
                handleConversationMessage(session, userId, username, convIdStr, cleanContent, tempId, messageType);
            } else {
                handlePublicMessage(userId, username, cleanContent);
            }

        } catch (Exception e) {
            log.error("Message processing error", e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            removeUser(userId);
            sessionManager.userOffline(userId);
            log.info("User [{}] disconnected", session.getAttributes().get("username"));
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket transport error: {}", exception.getMessage());
    }

    // ==================== ACK 处理（修复：统一使用 CacheKeys.WS_ACK） ====================

    private void handleAck(Long userId, Map<String, Object> msgMap) {
        Object msgIdObj = msgMap.get("messageId");
        if (msgIdObj == null) return;
        String messageId = msgIdObj.toString();

        Set<Long> convIds = USER_CONVERSATIONS.get(userId);
        if (convIds != null) {
            for (Long convId : convIds) {
                String ackKey = CacheKeys.WS_ACK + convId + ":" + messageId;
                cacheService.delete(ackKey);
            }
        }
        log.debug("ACK received: userId={}, messageId={}", userId, messageId);
    }

    private void pushUnacknowledgedMessages(Long userId, WebSocketSession session) {
        try {
            Set<Long> convIds = USER_CONVERSATIONS.get(userId);
            if (convIds == null || convIds.isEmpty()) return;

            for (Long convId : convIds) {
                List<ConversationMessage> recentMessages = conversationMessageMapper.selectList(
                        new LambdaQueryWrapper<ConversationMessage>()
                                .eq(ConversationMessage::getConversationId, convId)
                                .ne(ConversationMessage::getSenderId, userId)
                                .orderByDesc(ConversationMessage::getCreatedAt)
                                .last("limit 50"));

                for (ConversationMessage msg : recentMessages) {
                    Map<String, Object> wrapper = new LinkedHashMap<>();
                    wrapper.put("type", "message");
                    wrapper.put("conversationId", convId.toString());
                    wrapper.put("message", toMessageMap(msg));
                    wrapper.put("offline", true);
                    sendJson(session, Result.success(wrapper));
                }
            }
        } catch (Exception e) {
            log.warn("推送离线消息失败: userId={}, error={}", userId, e.getMessage());
        }
    }

    // ==================== 消息处理 ====================

    private void handleConversationMessage(WebSocketSession senderSession, Long userId, String username,
                                           String conversationIdStr, String content, String tempId,
                                           int messageType) {
        try {
            Long conversationId = Long.parseLong(conversationIdStr);

            ConversationMember member = conversationMemberMapper.selectOne(
                    new LambdaQueryWrapper<ConversationMember>()
                            .eq(ConversationMember::getConversationId, conversationId)
                            .eq(ConversationMember::getUserId, userId)
                            .last("limit 1"));
            if (member == null) {
                sendError(senderSession, "Not a member of this conversation");
                return;
            }

            ConversationMessage conversationMessage = new ConversationMessage();
            conversationMessage.setConversationId(conversationId);
            conversationMessage.setSenderId(userId);
            conversationMessage.setSenderName(username);
            conversationMessage.setContent(content);
            conversationMessage.setMessageType(messageType);
            conversationMessage.setDeliveryStatus("sent");
            conversationMessage.setCreatedAt(LocalDateTime.now());
            conversationMessageMapper.insert(conversationMessage);

            if (tempId != null && !tempId.isEmpty()) {
                Map<String, Object> ack = new LinkedHashMap<>();
                ack.put("type", "ack");
                ack.put("tempId", tempId);
                ack.put("messageId", conversationMessage.getId().toString());
                ack.put("status", "delivered");
                sendJson(senderSession, ack);
            }

            // 通过 Redis Pub/Sub 跨实例广播
            broadcastToConversationViaRedis(conversationId, conversationMessage);

        } catch (NumberFormatException e) {
            sendError(senderSession, "Invalid conversationId");
        } catch (Exception e) {
            log.error("Conversation message error", e);
        }
    }

    private void handlePublicMessage(Long userId, String username, String content) {
        try {
            ChatMessage chatMsg = new ChatMessage();
            chatMsg.setSenderId(userId);
            chatMsg.setSenderName(username);
            chatMsg.setContent(content);
            chatMsg.setCreateTime(LocalDateTime.now());
            chatMsg.setType(0);
            chatMessageMapper.insert(chatMsg);

            // 通过 Redis Pub/Sub 跨实例广播
            broadcastPublicViaRedis(chatMsg);
            writeConversationMessage(userId, username, content);

        } catch (Exception e) {
            log.error("Public message error", e);
        }
    }

    private void writeConversationMessage(Long userId, String username, String content) {
        try {
            Conversation publicConv = conversationMapper.selectOne(
                    new LambdaQueryWrapper<Conversation>()
                            .eq(Conversation::getBizKey, PUBLIC_CHANNEL_BIZ_KEY)
                            .last("limit 1"));
            if (publicConv == null) return;

            ConversationMessage cm = new ConversationMessage();
            cm.setConversationId(publicConv.getId());
            cm.setSenderId(userId);
            cm.setSenderName(username);
            cm.setContent(content);
            cm.setMessageType(0);
            cm.setDeliveryStatus("sent");
            cm.setCreatedAt(LocalDateTime.now());
            conversationMessageMapper.insert(cm);
        } catch (Exception e) {
            log.warn("Failed to dual-write public message to conversation_message", e);
        }
    }

    // ==================== Redis Pub/Sub 广播 ====================

    private void broadcastToConversationViaRedis(Long conversationId, ConversationMessage msg) {
        try {
            Map<String, Object> wrapper = new LinkedHashMap<>();
            wrapper.put("type", "conversation_message");
            wrapper.put("conversationId", conversationId.toString());
            wrapper.put("message", toMessageMap(msg));
            String json = OBJECT_MAPPER.writeValueAsString(Result.success(wrapper));

            RedisPubSubListener.BroadcastMessage broadcast = new RedisPubSubListener.BroadcastMessage();
            broadcast.setType("conversation");
            broadcast.setConversationId(conversationId);
            broadcast.setSenderId(msg.getSenderId());
            broadcast.setMessageJson(json);
            broadcast.setMessageId(msg.getId());
            broadcast.setSenderName(msg.getSenderName());
            broadcast.setContent(msg.getContent());
            redisPubSubListener.publish(broadcast);
        } catch (Exception e) {
            log.error("Redis broadcast error", e);
        }
    }

    private void broadcastPublicViaRedis(ChatMessage msg) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(Result.success(msg));

            RedisPubSubListener.BroadcastMessage broadcast = new RedisPubSubListener.BroadcastMessage();
            broadcast.setType("public");
            broadcast.setSenderId(null); // 公共消息不跳过任何人
            broadcast.setMessageJson(json);
            redisPubSubListener.publish(broadcast);
        } catch (Exception e) {
            log.error("Redis public broadcast error", e);
        }
    }

    /**
     * 处理来自 Redis Pub/Sub 的广播消息（转发给本地连接）
     */
    public void handleBroadcast(RedisPubSubListener.BroadcastMessage broadcast) {
        try {
            TextMessage textMessage = new TextMessage(broadcast.getMessageJson());

            if ("public".equals(broadcast.getType())) {
                ONLINE_USERS.forEach((id, sess) -> {
                    if (sess.isOpen()) {
                        try {
                            synchronized (sess) {
                                sess.sendMessage(textMessage);
                            }
                        } catch (IOException e) {
                            log.error("Failed to send to user {}", id, e);
                        }
                    }
                });
            } else if ("conversation".equals(broadcast.getType())) {
                Long conversationId = broadcast.getConversationId();
                Set<Long> memberIds = CONVERSATION_MEMBERS.get(conversationId);
                if (memberIds == null || memberIds.isEmpty()) return;

                for (Long memberId : memberIds) {
                    if (memberId.equals(broadcast.getSenderId())) continue;
                    WebSocketSession memberSession = ONLINE_USERS.get(memberId);
                    if (memberSession != null && memberSession.isOpen()) {
                        try {
                            synchronized (memberSession) {
                                memberSession.sendMessage(textMessage);
                            }
                        } catch (IOException e) {
                            log.error("Failed to send to user {}", memberId, e);
                        }
                    } else {
                        if (chatMessageProducer != null) {
                            chatMessageProducer.sendOfflinePush(
                                    memberId, conversationId, broadcast.getMessageId(),
                                    broadcast.getSenderName(), broadcast.getContent());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Handle broadcast error", e);
        }
    }

    // ==================== 外部调用接口 ====================

    public void notifyNewMessage(Long conversationId, ConversationMessage message) {
        List<ConversationMember> members = conversationMemberMapper.selectList(
                new LambdaQueryWrapper<ConversationMember>()
                        .eq(ConversationMember::getConversationId, conversationId));
        for (ConversationMember m : members) {
            if (m.getUserId() != null) {
                CONVERSATION_MEMBERS.computeIfAbsent(conversationId, k -> ConcurrentHashMap.newKeySet()).add(m.getUserId());
            }
        }
        broadcastToConversationViaRedis(conversationId, message);
    }

    // ==================== 会话加载 ====================

    private void loadUserConversations(Long userId) {
        List<ConversationMember> memberships = conversationMemberMapper.selectList(
                new LambdaQueryWrapper<ConversationMember>()
                        .eq(ConversationMember::getUserId, userId));

        Set<Long> conversationIds = memberships.stream()
                .map(ConversationMember::getConversationId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        USER_CONVERSATIONS.put(userId, conversationIds);
        for (Long convId : conversationIds) {
            CONVERSATION_MEMBERS.computeIfAbsent(convId, k -> ConcurrentHashMap.newKeySet()).add(userId);
        }
    }

    private static void removeUser(Long userId) {
        ONLINE_USERS.remove(userId);
        Set<Long> conversationIds = USER_CONVERSATIONS.remove(userId);
        if (conversationIds != null) {
            for (Long convId : conversationIds) {
                Set<Long> members = CONVERSATION_MEMBERS.get(convId);
                if (members != null) {
                    members.remove(userId);
                    if (members.isEmpty()) {
                        CONVERSATION_MEMBERS.remove(convId);
                    }
                }
            }
        }
    }

    // ==================== 工具方法 ====================

    private Map<String, Object> toMessageMap(ConversationMessage message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", message.getId() == null ? "" : message.getId().toString());
        data.put("senderId", message.getSenderId() == null ? "" : message.getSenderId().toString());
        data.put("senderName", message.getSenderName());
        data.put("content", message.getContent());
        data.put("createTime", message.getCreatedAt() == null ? "" : message.getCreatedAt().toString());
        data.put("type", message.getMessageType() == null ? 0 : message.getMessageType());
        data.put("deliveryStatus", message.getDeliveryStatus() == null ? "sent" : message.getDeliveryStatus());
        return data;
    }

    private void sendJson(WebSocketSession session, Object data) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(data);
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (IOException e) {
            log.warn("Failed to send JSON to user", e);
        }
    }

    private void sendError(WebSocketSession session, String errorMsg) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(Result.fail(errorMsg));
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (IOException e) {
            log.warn("Failed to send error to user", e);
        }
    }
}