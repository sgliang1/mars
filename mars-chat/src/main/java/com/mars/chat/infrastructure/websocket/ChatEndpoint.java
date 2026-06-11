package com.mars.chat.infrastructure.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mars.chat.domain.message.ChatMessage;
import com.mars.chat.domain.message.ChatMessageMapper;
import com.mars.chat.domain.message.SensitiveFilter;
import com.mars.common.Result;
import com.mars.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

// �?关键修正：Spring Boot 3 必须使用 jakarta 包，而非 javax
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import org.springframework.web.socket.server.standard.SpringConfigurator;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@ServerEndpoint(value = "/mars-chat/ws")
public class ChatEndpoint {

    // 静态变量保存所有在线连�?(Key: userId, Value: Session)
    private static final ConcurrentHashMap<Long, Session> ONLINE_USERS = new ConcurrentHashMap<>();

    // 由于 @ServerEndpoint 是多例模式（每个连接一个实例），Spring 无法直接 @Autowired 注入
    // 必须通过静态变量和 setApplicationContext 方法手动获取 Bean
    private static ApplicationContext applicationContext;

    public static void setApplicationContext(ApplicationContext context) {
        ChatEndpoint.applicationContext = context;
    }

    private Session session;
    private Long userId;
    private String username;

    /**
     * 连接建立时触�?
     */
    @OnOpen
    public void onOpen(Session session) {
        this.session = session;

        // 1. �?URL 参数获取 Token
        Map<String, List<String>> params = session.getRequestParameterMap();
        List<String> tokens = params.get("token");

        if (tokens == null || tokens.isEmpty()) {
            closeSession("未授�? 缺少Token");
            return;
        }

        // ==============================================================
        // �?核心逻辑：真实鉴�?(符合网络实名制要�?
        // ==============================================================
        try {
            String token = tokens.get(0);

            // 2. 解析 Token (依赖 mars-common �?JwtUtil)
            // 注意：JwtUtil 内部静态变�?KEY 必须已被 Spring 初始�?(需扫描 common �?
            Claims claims = JwtUtil.parseToken(token);

            // 3. 绑定真实用户信息
            this.userId = Long.parseLong(claims.get("userId").toString());
            this.username = claims.get("username").toString();

            // 4. 存入在线列表
            ONLINE_USERS.put(this.userId, session);
            log.info("实名用户 [{} - {}] 连接成功，当前在线人�? {}", userId, username, ONLINE_USERS.size());

        } catch (Exception e) {
            // 鉴权失败 (Token过期、伪造、密钥不匹配�?，强制断开
            log.error("WebSocket鉴权失败: {}", e.getMessage());
            closeSession("Token无效或已过期");
        }
    }

    /**
     * 收到客户端消息时触发
     */
    @OnMessage
    public void onMessage(String message, Session session) {
        // 如果未通过鉴权，不处理任何消息
        if (this.userId == null) {
            return;
        }

        try {
            // 1. 解析前端发来�?JSON 消息
            ObjectMapper om = new ObjectMapper();
            Map<String, String> msgMap = om.readValue(message, Map.class);
            String rawContent = msgMap.get("content");

            if (rawContent == null || rawContent.trim().isEmpty()) {
                return;
            }

            // 2. 🛡�?敏感词过�?(合规核心)
            // 必须�?ApplicationContext 获取 Bean，因�?ChatEndpoint 不是单例 Bean
            if (applicationContext != null) {
                SensitiveFilter filter = applicationContext.getBean(SensitiveFilter.class);
                String cleanContent = filter.filter(rawContent);

                // 3. 💾 持久化存�?(合规核心 - 聊天记录留存 6 个月)
                ChatMessage chatMsg = new ChatMessage();
                chatMsg.setSenderId(this.userId);
                chatMsg.setSenderName(this.username);
                chatMsg.setContent(cleanContent); // 存入数据库的是过滤后的内�?
                chatMsg.setCreateTime(LocalDateTime.now());
                chatMsg.setType(0); // 默认为文本消�?

                ChatMessageMapper mapper = applicationContext.getBean(ChatMessageMapper.class);
                mapper.insert(chatMsg);

                // 4. 广播消息给所有在线用�?
                broadcast(chatMsg);
            } else {
                log.error("ApplicationContext 未注入，无法获取 Service 组件");
            }

        } catch (Exception e) {
            log.error("消息处理异常", e);
        }
    }

    /**
     * 连接关闭时触�?
     */
    @OnClose
    public void onClose() {
        if (this.userId != null) {
            ONLINE_USERS.remove(this.userId);
            log.info("用户 [{}] 断开连接", username);
        }
    }

    /**
     * 发生错误时触�?
     */
    @OnError
    public void onError(Session session, Throwable error) {
        log.error("WebSocket error: " + error.getMessage());
    }

    /**
     * 广播消息工具方法
     */
    private void broadcast(ChatMessage msg) throws IOException {
        ObjectMapper om = new ObjectMapper();

        // �?修复 1: 注册 JavaTimeModule 以支�?LocalDateTime
        om.registerModule(new JavaTimeModule());

        // �?修复 2: 禁用"写为时间�?，确保输出为 ISO-8601 字符�?("2026-01-18T...")
        // 这样前端 Flutter �?DateTime.parse() 才能正确解析
        om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 统一返回 Result 格式，方便前端处�?
        String json = om.writeValueAsString(Result.success(msg));

        ONLINE_USERS.forEach((id, sess) -> {
            if (sess.isOpen()) {
                try {
                    // 使用 getBasicRemote() 同步发�?
                    sess.getBasicRemote().sendText(json);
                } catch (IOException e) {
                    log.error("发送消息给用户 {} 失败", id, e);
                }
            }
        });
    }

    /**
     * 关闭连接工具方法
     */
    private void closeSession(String reason) {
        try {
            session.close(new CloseReason(CloseReason.CloseCodes.CANNOT_ACCEPT, reason));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
