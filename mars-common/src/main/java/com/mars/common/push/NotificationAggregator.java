package com.mars.common.push;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;

/**
 * 通知聚合器
 * 使用 Redis 窗口聚合同类通知，避免用户短时间内收到大量零散推送
 *
 * 策略：30 秒窗口 + 最多 5 条合并
 * - 第一条通知到达 → 写入 Redis，返回 null（暂不推送）
 * - 30s 内同类通知到达 → 追加到列表，返回 null
 * - 30s 到期 / 列表满 5 条 → 返回合并后的聚合推送文案
 */
@Component
public class NotificationAggregator {

    private static final Logger log = LoggerFactory.getLogger(NotificationAggregator.class);
    private static final String KEY_PREFIX = "mars:push:agg:";
    private static final Duration WINDOW = Duration.ofSeconds(30);
    private static final int MAX_BATCH = 5;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 尝试聚合通知
     * @param userId    目标用户
     * @param category  通知分类 (like/comment/follow)
     * @param actorName 触发者名称
     * @param sourceType 来源类型
     * @return null 表示已聚合暂不推送; 非 null 表示应立即推送的合并文案
     */
    public String tryAggregate(Long userId, String category, String actorName, String sourceType) {
        String key = KEY_PREFIX + userId + ":" + category;

        try {
            List<AggEntry> entries = getEntries(key);
            entries.add(new AggEntry(actorName, sourceType));

            if (entries.size() >= MAX_BATCH) {
                // 达到上限，立即合并推送
                redisTemplate.delete(key);
                return buildMergedText(category, entries);
            }

            // 写回 Redis，设置 TTL（第一条时启动窗口）
            redisTemplate.opsForValue().set(key, MAPPER.writeValueAsString(entries), WINDOW);
            return null; // 暂不推送

        } catch (Exception e) {
            log.warn("通知聚合异常: {}", e.getMessage());
            // 异常时降级为即时推送
            return buildSingleText(category, actorName, sourceType);
        }
    }

    /**
     * 强制刷新指定用户的所有待聚合通知（供定时任务使用）
     */
    public String flush(Long userId, String category) {
        String key = KEY_PREFIX + userId + ":" + category;
        List<AggEntry> entries = getEntries(key);
        if (entries.isEmpty()) return null;
        redisTemplate.delete(key);
        return buildMergedText(category, entries);
    }

    @SuppressWarnings("unchecked")
    private List<AggEntry> getEntries(String key) {
        try {
            Object raw = redisTemplate.opsForValue().get(key);
            if (raw instanceof String json) {
                return MAPPER.readValue(json, new TypeReference<List<AggEntry>>() {});
            }
        } catch (Exception e) {
            log.debug("解析聚合缓存失败: {}", e.getMessage());
        }
        return new ArrayList<>();
    }

    private String buildMergedText(String category, List<AggEntry> entries) {
        Set<String> names = new LinkedHashSet<>();
        for (AggEntry e : entries) names.add(e.actorName);

        String first = names.iterator().next();
        int count = entries.size();

        return switch (category) {
            case "like" -> count == 1
                    ? first + " 赞了你的帖子"
                    : first + " 等" + count + "人赞了你的帖子";
            case "comment" -> count == 1
                    ? first + " 评论了你的帖子"
                    : count + "条新评论";
            case "follow" -> count == 1
                    ? first + " 关注了你"
                    : first + " 等" + count + "人关注了你";
            default -> count == 1
                    ? "你有1条新通知"
                    : "你有" + count + "条新通知";
        };
    }

    private String buildSingleText(String category, String actorName, String sourceType) {
        return switch (category) {
            case "like" -> actorName + " 赞了你的帖子";
            case "comment" -> actorName + " 评论了你的帖子";
            case "follow" -> actorName + " 关注了你";
            default -> "你有1条新通知";
        };
    }

    private static class AggEntry {
        public String actorName;
        public String sourceType;

        public AggEntry() {}
        public AggEntry(String actorName, String sourceType) {
            this.actorName = actorName;
            this.sourceType = sourceType;
        }
    }
}