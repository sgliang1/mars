package com.interstellar.chat.infrastructure.presence;

import com.interstellar.chat.infrastructure.websocket.WebSocketSessionManager;
import com.interstellar.common.cache.CacheKeys;
import com.interstellar.common.cache.CacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户在线状态服务
 * - recordActivity: 节流写入 last_active_at（每用户 5 分钟最多一次）
 * - getPresence: 查询在线状态 + 最后活跃时间
 * - batchPresence: 批量查询
 */
@Slf4j
@Service
public class PresenceService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CacheService cacheService;

    @Lazy
    @Autowired
    private WebSocketSessionManager sessionManager;

    private static final DateTimeFormatter DB_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 记录用户活跃（节流：每 5 分钟最多写一次 DB）
     */
    @Async
    public void recordActivity(Long userId) {
        try {
            String throttleKey = CacheKeys.key(CacheKeys.USER_ACTIVE_THROTTLE, userId);
            if (cacheService.hasKey(throttleKey)) {
                return;
            }
            cacheService.set(throttleKey, "1", CacheKeys.USER_ACTIVE_THROTTLE_TTL);
            jdbcTemplate.update(
                    "UPDATE user_profile SET last_active_at = NOW() WHERE user_id = ?",
                    userId
            );
        } catch (Exception e) {
            log.warn("记录用户活跃失败: userId={}, error={}", userId, e.getMessage());
        }
    }

    /**
     * 查询单个用户的在线状态
     */
    public Map<String, Object> getPresence(Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("online", sessionManager.isOnline(userId));
        result.put("lastActiveAt", queryLastActiveAt(userId));
        return result;
    }

    /**
     * 批量查询多个用户的在线状态
     */
    public List<Map<String, Object>> batchPresence(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量查询 last_active_at
        String placeholders = userIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "SELECT user_id, last_active_at FROM user_profile WHERE user_id IN (" + placeholders + ")";
        Map<Long, String> lastActiveMap = new HashMap<>();

        jdbcTemplate.query(sql, rs -> {
            Long uid = rs.getLong("user_id");
            LocalDateTime ts = rs.getTimestamp("last_active_at") != null
                    ? rs.getTimestamp("last_active_at").toLocalDateTime() : null;
            if (ts != null) {
                lastActiveMap.put(uid, ts.format(DB_FORMATTER));
            }
        }, userIds.toArray());

        List<Map<String, Object>> results = new ArrayList<>();
        for (Long userId : userIds) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("userId", userId);
            entry.put("online", sessionManager.isOnline(userId));
            entry.put("lastActiveAt", lastActiveMap.get(userId));
            results.add(entry);
        }
        return results;
    }

    private String queryLastActiveAt(Long userId) {
        try {
            List<String> results = jdbcTemplate.queryForList(
                    "SELECT last_active_at FROM user_profile WHERE user_id = ?",
                    String.class, userId
            );
            return results.isEmpty() ? null : results.get(0);
        } catch (Exception e) {
            log.warn("查询 last_active_at 失败: userId={}", userId);
            return null;
        }
    }
}
