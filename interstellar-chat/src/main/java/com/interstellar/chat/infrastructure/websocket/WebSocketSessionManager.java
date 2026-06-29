package com.interstellar.chat.infrastructure.websocket;

import com.interstellar.chat.infrastructure.presence.PresenceService;
import com.interstellar.common.cache.CacheKeys;
import com.interstellar.common.cache.CacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 连接状态管理
 * - 本地维护 ConcurrentHashMap 加速在线判断
 * - Redis 维护跨实例在线状态（TTL 2 分钟，心跳续期）
 * - 断开时清除本地 + Redis 状态
 */
@Component
public class WebSocketSessionManager {

    private static final Logger log = LoggerFactory.getLogger(WebSocketSessionManager.class);

    /** 本地在线状态缓存（当前实例） */
    private static final ConcurrentHashMap<Long, String> LOCAL_ONLINE = new ConcurrentHashMap<>();

    @Autowired
    private CacheService cacheService;

    @Lazy
    @Autowired(required = false)
    private PresenceService presenceService;

    /**
     * 用户上线：注册到 Redis 并记录本地状态
     * @param userId 用户 ID
     * @param serverInstanceId 当前服务实例标识
     */
    public void userOnline(Long userId, String serverInstanceId) {
        LOCAL_ONLINE.put(userId, serverInstanceId);
        String redisKey = CacheKeys.key(CacheKeys.WS_ONLINE, userId);
        cacheService.set(redisKey, serverInstanceId, CacheKeys.WS_ONLINE_TTL);
        log.debug("用户上线: userId={}, instance={}", userId, serverInstanceId);
        // 记录用户活跃时间
        if (presenceService != null) {
            presenceService.recordActivity(userId);
        }
    }

    /**
     * 用户下线：清除 Redis 和本地状态
     */
    public void userOffline(Long userId) {
        LOCAL_ONLINE.remove(userId);
        String redisKey = CacheKeys.key(CacheKeys.WS_ONLINE, userId);
        cacheService.delete(redisKey);
        log.debug("用户下线: userId={}", userId);
    }

    /**
     * 心跳续期：刷新 Redis TTL
     */
    public void heartbeat(Long userId) {
        String redisKey = CacheKeys.key(CacheKeys.WS_ONLINE, userId);
        cacheService.expire(redisKey, CacheKeys.WS_ONLINE_TTL);
    }

    /**
     * 判断用户是否在线（优先本地，再查 Redis）
     */
    public boolean isOnline(Long userId) {
        if (LOCAL_ONLINE.containsKey(userId)) {
            return true;
        }
        return cacheService.hasKey(CacheKeys.key(CacheKeys.WS_ONLINE, userId));
    }

    /**
     * 获取本地在线用户数
     */
    public int localOnlineCount() {
        return LOCAL_ONLINE.size();
    }

    /**
     * 获取本地在线用户 ID 集合
     */
    public Set<Long> localOnlineUserIds() {
        return LOCAL_ONLINE.keySet();
    }
}