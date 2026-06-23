package com.mars.interaction.domain.filter;

import com.mars.common.cache.CacheKeys;
import com.mars.common.cache.CacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 用户过滤服务 — 读取 Redis 中的 block/mute 集合
 * 供通知/评论模块过滤使用
 * 数据由 mars-user RelationService 在 block/unblock/mute/unmute 时写入
 */
@Service
public class UserFilterService {

    @Autowired
    private CacheService cacheService;

    public Set<Long> getBlockedIds(Long userId) {
        if (userId == null) return Collections.emptySet();
        Set<Long> ids = cacheService.setMembers(CacheKeys.key(CacheKeys.RELATION_BLOCK_IDS, userId));
        return ids != null ? ids : Collections.emptySet();
    }

    public Set<Long> getMutedIds(Long userId) {
        if (userId == null) return Collections.emptySet();
        Set<Long> ids = cacheService.setMembers(CacheKeys.key(CacheKeys.RELATION_MUTE_IDS, userId));
        return ids != null ? ids : Collections.emptySet();
    }

    public Set<Long> getFilteredIds(Long userId) {
        Set<Long> filtered = new HashSet<>();
        filtered.addAll(getBlockedIds(userId));
        filtered.addAll(getMutedIds(userId));
        return filtered;
    }

    public boolean isFiltered(Long currentUserId, Long targetUserId) {
        if (currentUserId == null || targetUserId == null) return false;
        return getFilteredIds(currentUserId).contains(targetUserId);
    }
}
