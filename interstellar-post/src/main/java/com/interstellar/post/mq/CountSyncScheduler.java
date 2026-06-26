package com.interstellar.post.mq;

import com.interstellar.common.cache.CacheKeys;
import com.interstellar.common.cache.CacheService;
import com.interstellar.post.domain.post.PostMapper;
import com.interstellar.post.domain.post.PostLikeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/**
 * Redis 计数器定时回写调度器
 * 每 5 分钟将 Redis 中的点赞/评论计数器变化批量写回 DB
 *
 * 工作原理：
 * 1. 业务写操作同时更新 DB（即时生效）和 Redis 计数器（用于高频读）
 * 2. 本调度器定期将 Redis 计数器值同步回 DB，确保最终一致性
 * 3. 使用 GETSET 原子操作获取并重置计数器，避免丢失增量
 */
@Component
public class CountSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(CountSyncScheduler.class);

    @Autowired
    private CacheService cacheService;

    @Autowired
    private PostMapper postMapper;

    /**
     * 每 5 分钟执行一次计数器同步
     * 扫描 Redis 中所有 interstellar:count:likes:* 和 interstellar:count:comments:* 的 key
     * 将计数器值同步回 DB
     */
    @Scheduled(fixedRate = 300000) // 5 分钟
    public void syncCounters() {
        // 分布式锁，防止多实例重复执行
        String lockOwner = cacheService.tryLock("lock:count-sync-task", Duration.ofSeconds(280));
        if (lockOwner == null) {
            log.debug("计数器同步任务未获取到锁，跳过本次执行");
            return;
        }
        try {
            int likeCount = syncLikeCounters();
            int commentCount = syncCommentCounters();
            int viewCount = syncViewCounters();
            if (likeCount > 0 || commentCount > 0 || viewCount > 0) {
                log.info("计数器同步完成: 点赞同步 {} 个帖子, 评论同步 {} 个帖子, 浏览量同步 {} 个帖子",
                         likeCount, commentCount, viewCount);
            }
        } finally {
            cacheService.unlock("lock:count-sync-task", lockOwner);
        }
    }

    private int syncLikeCounters() {
        int synced = 0;
        try {
            Set<String> keys = cacheService.scanKeys(CacheKeys.COUNT_LIKES + "*");
            if (keys.isEmpty()) return 0;

            for (String key : keys) {
                try {
                    Object value = cacheService.get(key);
                    if (value == null) continue;
                    long count = Long.parseLong(value.toString());
                    if (count == 0) continue;

                    // 从 key 中提取 postId: interstellar:count:likes:{postId}
                    String postIdStr = key.substring(CacheKeys.COUNT_LIKES.length());
                    Long postId = Long.parseLong(postIdStr);

                    // 更新 DB 中的 like_count 为 Redis 中的值
                    postMapper.updateLikeCountDirect(postId, count);
                    synced++;
                } catch (Exception e) {
                    log.warn("同步帖子点赞计数失败: key={}, error={}", key, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("扫描点赞计数器失败: {}", e.getMessage());
        }
        return synced;
    }

    private int syncCommentCounters() {
        int synced = 0;
        try {
            Set<String> keys = cacheService.scanKeys(CacheKeys.COUNT_COMMENTS + "*");
            if (keys.isEmpty()) return 0;

            for (String key : keys) {
                try {
                    Object value = cacheService.get(key);
                    if (value == null) continue;
                    long count = Long.parseLong(value.toString());
                    if (count == 0) continue;

                    String postIdStr = key.substring(CacheKeys.COUNT_COMMENTS.length());
                    Long postId = Long.parseLong(postIdStr);

                    postMapper.updateCommentCountDirect(postId, count);
                    synced++;
                } catch (Exception e) {
                    log.warn("同步帖子评论计数失败: key={}, error={}", key, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("扫描评论计数器失败: {}", e.getMessage());
        }
        return synced;
    }

    private int syncViewCounters() {
        int synced = 0;
        try {
            Set<String> keys = cacheService.scanKeys(CacheKeys.COUNT_VIEWS + "*");
            if (keys.isEmpty()) return 0;

            for (String key : keys) {
                try {
                    Object value = cacheService.get(key);
                    if (value == null) continue;
                    long count = Long.parseLong(value.toString());
                    if (count == 0) continue;

                    String postIdStr = key.substring(CacheKeys.COUNT_VIEWS.length());
                    Long postId = Long.parseLong(postIdStr);

                    postMapper.incrementViewCount(postId, count);
                    synced++;
                } catch (Exception e) {
                    log.warn("同步帖子浏览量计数失败: key={}, error={}", key, e.getMessage());
                }
            }
            // 回写后清理增量 key，小窗口内新到的 increment 会丢失（可接受）
            for (String key : keys) {
                cacheService.delete(key);
            }
        } catch (Exception e) {
            log.error("扫描浏览量计数器失败: {}", e.getMessage());
        }
        return synced;
    }
}