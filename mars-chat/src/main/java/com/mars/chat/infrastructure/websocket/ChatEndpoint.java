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
import com.mars.common.Result;
import com.mars.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@ServerEndpoint(value = "/mars-chat/ws")
public class ChatEndpoint {

    private static final String PUBLIC_CHANNEL_BIZ_KEY = "public-lobby";

    private static final ConcurrentHashMap<Long, Session> ONLINE_USERS = new ConcurrentHashMap<>();
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

    static {
        HEARTBEAT_EXECUTOR.scheduleAtFixedRate(() -> {
            try {
                List<Long> staleUserIds = new ArrayList<>();
                ONLINE_USERS.forEach((userId, session) -> {
                    if (!session.isOpen()) {
                        staleUserIds.add(userId);
                    }
                });
                staleUserIds.forEach(ChatEndpoint::removeUser);
            } catch (Exception e) {
                log.warn("Heartbeat cleanup error", e);
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    private static ApplicationContext applicationContext;

    public static void setApplicationContext(ApplicationContext context) {
        ChatEndpoint.applicationContext = context;
    }

    private Session session;
    private Long userId;
    private String username;

    @OnOpen
    public void onOpen(Session session) {
        this.session = session;

        Map<String, List<String>> params = session.getRequestParameterMap();
        List<String> tokens = params.get("token");

        if (tokens == null || tokens.isEmpty()) {
            closeSession("Unauthorized: missing token");
            return;
        }

        try {
            String token = tokens.get(0);
            Claims claims = JwtUtil.parseToken(token);

            this.userId = Long.parseLong(claims.get("userId").toString());
            this.username = claims.get("username").toString();

            Session oldSession = ONLINE_USERS.put(this.userId, session);
            if (oldSession != null && oldSession.isOpen()) {
                try {
                    oldSession.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "New connection replaced"));
                } catch (IOException e) {
                    log.warn("Failed to close old session", e);
                }
            }

            session.setMaxIdleTimeout(60000);
            session.setMaxTextMessageBufferSize(8192);

            loadUserConversations(this.userId);

            log.info("User [{} - {}] connected, online count: {}", userId, username, ONLINE_USERS.size());

        } catch (Exception e) {
            log.error("WebSocket auth failed: {}", e.getMessage());
            closeSession("Invalid or expired token");
        }
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        if (this.userId == null) return;

        try {
            Map<String, String> msgMap = OBJECT_MAPPER.readValue(message, Map.class);
            String rawContent = msgMap.get("content");

            if (rawContent == null || rawContent.trim().isEmpty()) return;

            SensitiveFilter filter = applicationContext.getBean(SensitiveFilter.class);
            String cleanContent = filter.filter(rawContent);

            String convIdStr = msgMap.get("conversationId");
            if (convIdStr != null && !convIdStr.isBlank()) {
                handleConversationMessage(convIdStr, cleanContent);
            } else {
                handlePublicMessage(cleanContent);
            }

        } catch (Exception e) {
            log.error("Message processing error", e);
        }
    }

    @OnClose
    public void onClose() {
        if (this.userId != null) {
            removeUser(this.userId);
            log.info("User [{}] disconnected", username);
        }
    }

    @OnError
    public void onError(Session session, Throwable error) {
        log.error("WebSocket error: {}", error.getMessage());
    }

    private void handleConversationMessage(String conversationIdStr, String content) {
        try {
            Long conversationId = Long.parseLong(conversationIdStr);
            ConversationMemberMapper memberMapper = applicationContext.getBean(ConversationMemberMapper.class);
            ConversationMember member = memberMapper.selectOne(new LambdaQueryWrapper<ConversationMember>()
                    .eq(ConversationMember::getConversationId, conversationId)
                    .eq(ConversationMember::getUserId, this.userId)
                    .last("limit 1"));
            if (member == null) {
                sendError("Not a member of this conversation");
                return;
            }

            ConversationMessageMapper conversationMessageMapper = applicationContext.getBean(ConversationMessageMapper.class);
            ConversationMessage conversationMessage = new ConversationMessage();
            conversationMessage.setConversationId(conversationId);
            conversationMessage.setSenderId(this.userId);
            conversationMessage.setSenderName(this.username);
            conversationMessage.setContent(content);
            conversationMessage.setMessageType(0);
            conversationMessage.setDeliveryStatus("sent");
            conversationMessage.setCreatedAt(LocalDateTime.now());
            conversationMessageMapper.insert(conversationMessage);

            broadcastToConversation(conversationId, conversationMessage);
        } catch (NumberFormatException e) {
            sendError("Invalid conversationId");
        } catch (Exception e) {
            log.error("Conversation message error", e);
        }
    }

    private void handlePublicMessage(String content) {
        try {
            ChatMessage chatMsg = new ChatMessage();
            chatMsg.setSenderId(this.userId);
            chatMsg.setSenderName(this.username);
            chatMsg.setContent(content);
            chatMsg.setCreateTime(LocalDateTime.now());
            chatMsg.setType(0);

            ChatMessageMapper chatMessageMapper = applicationContext.getBean(ChatMessageMapper.class);
            chatMessageMapper.insert(chatMsg);

            broadcastPublic(chatMsg);

            writeConversationMessage(content);

        } catch (Exception e) {
            log.error("Public message error", e);
        }
    }

    private void writeConversationMessage(String content) {
        try {
            ConversationMapper conversationMapper = applicationContext.getBean(ConversationMapper.class);
            Conversation publicConv = conversationMapper.selectOne(new LambdaQueryWrapper<Conversation>()
                    .eq(Conversation::getBizKey, PUBLIC_CHANNEL_BIZ_KEY)
                    .last("limit 1"));
            if (publicConv == null) return;

            ConversationMessageMapper conversationMessageMapper = applicationContext.getBean(ConversationMessageMapper.class);
            ConversationMessage cm = new ConversationMessage();
            cm.setConversationId(publicConv.getId());
            cm.setSenderId(this.userId);
            cm.setSenderName(this.username);
            cm.setContent(content);
            cm.setMessageType(0);
            cm.setDeliveryStatus("sent");
            cm.setCreatedAt(LocalDateTime.now());
            conversationMessageMapper.insert(cm);
        } catch (Exception e) {
            log.warn("Failed to dual-write public message to conversation_message", e);
        }
    }

    private void broadcastToConversation(Long conversationId, ConversationMessage msg) {
        Set<Long> memberIds = CONVERSATION_MEMBERS.get(conversationId);
        if (memberIds == null || memberIds.isEmpty()) return;

        try {
            Map<String, Object> wrapper = new LinkedHashMap<>();
            wrapper.put("type", "conversation_message");
            wrapper.put("conversationId", conversationId.toString());
            wrapper.put("message", toMessageMap(msg));
            String json = OBJECT_MAPPER.writeValueAsString(Result.success(wrapper));

            for (Long memberId : memberIds) {
                Session memberSession = ONLINE_USERS.get(memberId);
                if (memberSession != null && memberSession.isOpen()) {
                    try {
                        memberSession.getBasicRemote().sendText(json);
                    } catch (IOException e) {
                        log.error("Failed to send to user {}", memberId, e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Broadcast error", e);
        }
    }

    private void broadcastPublic(ChatMessage msg) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(Result.success(msg));
            ONLINE_USERS.forEach((id, sess) -> {
                if (sess.isOpen()) {
                    try {
                        sess.getBasicRemote().sendText(json);
                    } catch (IOException e) {
                        log.error("Failed to send to user {}", id, e);
                    }
                }
            });
        } catch (Exception e) {
            log.error("Public broadcast error", e);
        }
    }

    private void loadUserConversations(Long userId) {
        ConversationMemberMapper memberMapper = applicationContext.getBean(ConversationMemberMapper.class);
        List<ConversationMember> memberships = memberMapper.selectList(new LambdaQueryWrapper<ConversationMember>()
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

    private void sendError(String errorMsg) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(Result.fail(errorMsg));
            session.getBasicRemote().sendText(json);
        } catch (IOException e) {
            log.warn("Failed to send error to user", e);
        }
    }

    private void closeSession(String reason) {
        try {
            session.close(new CloseReason(CloseReason.CloseCodes.CANNOT_ACCEPT, reason));
        } catch (IOException e) {
            log.warn("Failed to close session", e);
        }
    }
}