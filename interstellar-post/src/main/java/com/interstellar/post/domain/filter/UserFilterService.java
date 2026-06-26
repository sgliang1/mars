package com.interstellar.post.domain.filter;

import com.interstellar.common.cache.CacheKeys;
import com.interstellar.common.cache.CacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 用户过滤服务 — 读取 Redis 中的 block/mute 集合
 * 供 Feed / Search / Post / Notification 等模块过滤使用
 * 数据由 interstellar-user RelationService 在 block/unblock/mute/unmute 时写入
 */
@Service
public class UserFilterService {

    @Autowired
    private CacheService cacheService;

    /**
     * 获取当前用户拉黑的所有用户 ID
     */
    public Set<Long> getBlockedIds(Long userId) {
        if (userId == null) return Collections.emptySet();
        Set<Long> ids = cacheService.setMembers(CacheKeys.key(CacheKeys.RELATION_BLOCK_IDS, userId));
        return ids != null ? ids : Collections.emptySet();
    }

    /**
     * 获取当前用户静音的所有用户 ID
     */
    public Set<Long> getMutedIds(Long userId) {
        if (userId == null) return Collections.emptySet();
        Set<Long> ids = cacheService.setMembers(CacheKeys.key(CacheKeys.RELATION_MUTE_IDS, userId));
        return ids != null ? ids : Collections.emptySet();
    }

    /**
     * 获取当前用户需要过滤的所有用户 ID（block + mute 合集）
     */
    public Set<Long> getFilteredIds(Long userId) {
        Set<Long> filtered = new HashSet<>();
        filtered.addAll(getBlockedIds(userId));
        filtered.addAll(getMutedIds(userId));
        return filtered;
    }

    /**
     * 判断目标用户是否在过滤列表中
     */
    public boolean isFiltered(Long currentUserId, Long targetUserId) {
        if (currentUserId == null || targetUserId == null) return false;
        return getFilteredIds(currentUserId).contains(targetUserId);
    }
}