package com.interstellar.relation.domain.relation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interstellar.api.RelationFeignClient;
import com.interstellar.common.cache.CacheKeys;
import com.interstellar.common.cache.CacheService;
import com.interstellar.common.mq.MqTopics;
import com.interstellar.common.mq.NotificationMessage;
import com.interstellar.common.outbox.OutboxService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 社交关系服务（已从 interstellar-user 提取为独立服务）
 *
 * 跨服务依赖：
 * - follower_count / following_count → 通过 RelationFeignClient 调用 interstellar-user
 * - follow 通知 → 通过 OutboxService 写入发件箱，异步投递到 interstellar-interaction
 * - Redis 缓存 → 直接写入共享 Redis（block_ids, mute_ids 等）
 */
@Service
public class RelationService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private UserRelationMapper userRelationMapper;

    @Autowired
    private RelationFeignClient relationFeignClient;

    @Autowired
    private OutboxService outboxService;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private UserBlockMapper userBlockMapper;

    @Autowired
    private UserMuteMapper userMuteMapper;

    @Autowired
    private RelationEventService relationEventService;

    public List<Map<String, Object>> getFollowingList(Long userId) {
        return userRelationMapper.selectFollowingList(userId);
    }

    public List<Map<String, Object>> getFollowerList(Long userId) {
        return userRelationMapper.selectFollowerList(userId);
    }

    public boolean isFollowing(Long followerId, Long followedId) {
        String cacheKey = CacheKeys.relationKey(CacheKeys.RELATION_CHECK, followerId, followedId);
        Boolean cached = cacheService.get(cacheKey);
        if (cached != null) return cached;

        boolean result = userRelationMapper.isFollowing(followerId, followedId);
        cacheService.set(cacheKey, result, CacheKeys.RELATION_CHECK_TTL);
        return result;
    }

    public List<Map<String, Object>> getMutualFriends(Long userId) {
        return userRelationMapper.selectMutualFriends(userId);
    }

    public boolean isFollowedBy(Long currentUserId, Long targetUserId) {
        return userRelationMapper.isFollowing(targetUserId, currentUserId);
    }

    @Transactional
    public void follow(Long followerId, Long followedId, String followerName, String sourceType, Long sourceId) {
        if (followerId.equals(followedId)) {
            throw new IllegalArgumentException("不能关注自己");
        }
        // 幂等锁：防止并发重复关注
        String lockKey = "lock:follow:" + followerId + ":" + followedId;
        String lockOwner = cacheService.tryLock(lockKey, Duration.ofSeconds(3));
        if (lockOwner == null) {
            throw new IllegalStateException("操作过于频繁，请稍后再试");
        }
        try {
            if (isFollowing(followerId, followedId)) {
                throw new IllegalArgumentException("已关注该用户");
            }

            UserRelation relation = new UserRelation();
            relation.setFollowerId(followerId);
            relation.setFollowedId(followedId);
            relation.setCreatedAt(LocalDateTime.now());
            relation.setSourceType(sourceType);
            relation.setSourceId(sourceId);
            userRelationMapper.insert(relation);

            // 通过 Feign 更新 interstellar-user 的粉丝/关注计数
            try {
                relationFeignClient.updateFollowingCount(followerId, 1);
                relationFeignClient.updateFollowerCount(followedId, 1);
            } catch (Exception e) {
                // Feign 失败不阻断主流程，计数可通过定时任务补偿
                org.slf4j.LoggerFactory.getLogger(RelationService.class)
                        .warn("更新粉丝计数失败（Feign），后续补偿: followerId={}, followedId={}, error={}",
                                followerId, followedId, e.getMessage());
            }

            cacheService.delete(CacheKeys.relationKey(CacheKeys.RELATION_CHECK, followerId, followedId));
            cacheService.delete(CacheKeys.relationKey(CacheKeys.RELATION_CHECK, followedId, followerId));

            // 通过 Outbox 发送关注通知到 interstellar-interaction
            try {
                NotificationMessage msg = new NotificationMessage(
                        followedId, "interaction",
                        followerName != null ? followerName : "匿名用户",
                        "{\"actorId\":\"" + followerId + "\"}",
                        "follow", null);
                msg.setActorId(followerId);
                String bizKey = "follow:" + followerId + ":" + followedId;
                outboxService.save(MqTopics.NOTIFICATION, MqTopics.TAG_INTERACTION,
                        MAPPER.writeValueAsString(msg), bizKey);
            } catch (Exception ignored) {}

            relationEventService.recordEvent(followerId, followedId, "follow");
        } finally {
            cacheService.unlock(lockKey, lockOwner);
        }
    }

    @Transactional
    public void unfollow(Long followerId, Long followedId) {
        // 幂等锁：防止并发重复取关
        String lockKey = "lock:follow:" + followerId + ":" + followedId;
        String lockOwner = cacheService.tryLock(lockKey, Duration.ofSeconds(3));
        if (lockOwner == null) {
            throw new IllegalStateException("操作过于频繁，请稍后再试");
        }
        try {
            LambdaQueryWrapper<UserRelation> wrapper = new LambdaQueryWrapper<UserRelation>()
                    .eq(UserRelation::getFollowerId, followerId)
                    .eq(UserRelation::getFollowedId, followedId);
            int deleted = userRelationMapper.delete(wrapper);
            if (deleted == 0) {
                throw new IllegalArgumentException("未关注该用户");
            }

            // 通过 Feign 更新 interstellar-user 的粉丝/关注计数
            try {
                relationFeignClient.updateFollowingCount(followerId, -1);
                relationFeignClient.updateFollowerCount(followedId, -1);
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(RelationService.class)
                        .warn("更新粉丝计数失败（Feign），后续补偿: followerId={}, followedId={}, error={}",
                                followerId, followedId, e.getMessage());
            }

            cacheService.delete(CacheKeys.relationKey(CacheKeys.RELATION_CHECK, followerId, followedId));
            cacheService.delete(CacheKeys.relationKey(CacheKeys.RELATION_CHECK, followedId, followerId));

            relationEventService.recordEvent(followerId, followedId, "unfollow");
        } finally {
            cacheService.unlock(lockKey, lockOwner);
        }
    }

    // ==================== 拉黑 ====================

    @Transactional
    public void block(Long blockerId, Long blockedId) {
        if (blockerId.equals(blockedId)) {
            throw new IllegalArgumentException("不能拉黑自己");
        }
        // 检查是否已拉黑
        Long count = userBlockMapper.selectCount(new LambdaQueryWrapper<UserBlock>()
                .eq(UserBlock::getBlockerId, blockerId)
                .eq(UserBlock::getBlockedId, blockedId));
        if (count > 0) {
            throw new IllegalArgumentException("已拉黑该用户");
        }

        UserBlock block = new UserBlock();
        block.setBlockerId(blockerId);
        block.setBlockedId(blockedId);
        block.setCreatedAt(LocalDateTime.now());
        userBlockMapper.insert(block);

        // 拉黑后自动取消双向关注
        try { unfollow(blockerId, blockedId); } catch (Exception ignored) {}
        try { unfollow(blockedId, blockerId); } catch (Exception ignored) {}

        // 清除拉黑缓存
        cacheService.delete(CacheKeys.relationKey(CacheKeys.RELATION_BLOCK, blockerId, blockedId));

        // 维护拉黑 ID 集合缓存（供跨服务过滤使用）
        cacheService.setAdd(CacheKeys.key(CacheKeys.RELATION_BLOCK_IDS, blockerId), blockedId);
        cacheService.expire(CacheKeys.key(CacheKeys.RELATION_BLOCK_IDS, blockerId), CacheKeys.RELATION_BLOCK_IDS_TTL);

        relationEventService.recordEvent(blockerId, blockedId, "block");
    }

    @Transactional
    public void unblock(Long blockerId, Long blockedId) {
        int deleted = userBlockMapper.delete(new LambdaQueryWrapper<UserBlock>()
                .eq(UserBlock::getBlockerId, blockerId)
                .eq(UserBlock::getBlockedId, blockedId));
        if (deleted == 0) {
            throw new IllegalArgumentException("未拉黑该用户");
        }
        cacheService.delete(CacheKeys.relationKey(CacheKeys.RELATION_BLOCK, blockerId, blockedId));

        // 维护拉黑 ID 集合缓存
        cacheService.setRemove(CacheKeys.key(CacheKeys.RELATION_BLOCK_IDS, blockerId), blockedId);

        relationEventService.recordEvent(blockerId, blockedId, "unblock");
    }

    public boolean isBlocked(Long blockerId, Long blockedId) {
        String cacheKey = CacheKeys.relationKey(CacheKeys.RELATION_BLOCK, blockerId, blockedId);
        Boolean cached = cacheService.get(cacheKey);
        if (cached != null) return cached;

        Long count = userBlockMapper.selectCount(new LambdaQueryWrapper<UserBlock>()
                .eq(UserBlock::getBlockerId, blockerId)
                .eq(UserBlock::getBlockedId, blockedId));
        boolean result = count > 0;
        cacheService.set(cacheKey, result, CacheKeys.RELATION_BLOCK_TTL);
        return result;
    }

    public List<Map<String, Object>> getBlockList(Long userId) {
        return userBlockMapper.selectMaps(new LambdaQueryWrapper<UserBlock>()
                .eq(UserBlock::getBlockerId, userId)
                .orderByDesc(UserBlock::getCreatedAt));
    }

    // ==================== 静音 ====================

    public void mute(Long muterId, Long mutedId) {
        if (muterId.equals(mutedId)) {
            throw new IllegalArgumentException("不能静音自己");
        }
        Long count = userMuteMapper.selectCount(new LambdaQueryWrapper<UserMute>()
                .eq(UserMute::getMuterId, muterId)
                .eq(UserMute::getMutedId, mutedId));
        if (count > 0) {
            throw new IllegalArgumentException("已静音该用户");
        }

        UserMute mute = new UserMute();
        mute.setMuterId(muterId);
        mute.setMutedId(mutedId);
        mute.setCreatedAt(LocalDateTime.now());
        userMuteMapper.insert(mute);

        // 维护静音 ID 集合缓存（供跨服务过滤使用）
        cacheService.setAdd(CacheKeys.key(CacheKeys.RELATION_MUTE_IDS, muterId), mutedId);
        cacheService.expire(CacheKeys.key(CacheKeys.RELATION_MUTE_IDS, muterId), CacheKeys.RELATION_MUTE_IDS_TTL);
    }

    public void unmute(Long muterId, Long mutedId) {
        int deleted = userMuteMapper.delete(new LambdaQueryWrapper<UserMute>()
                .eq(UserMute::getMuterId, muterId)
                .eq(UserMute::getMutedId, mutedId));
        if (deleted == 0) {
            throw new IllegalArgumentException("未静音该用户");
        }

        // 维护静音 ID 集合缓存
        cacheService.setRemove(CacheKeys.key(CacheKeys.RELATION_MUTE_IDS, muterId), mutedId);
    }

    public boolean isMuted(Long muterId, Long mutedId) {
        Long count = userMuteMapper.selectCount(new LambdaQueryWrapper<UserMute>()
                .eq(UserMute::getMuterId, muterId)
                .eq(UserMute::getMutedId, mutedId));
        return count > 0;
    }

    public List<Map<String, Object>> getMuteList(Long userId) {
        return userMuteMapper.selectMaps(new LambdaQueryWrapper<UserMute>()
                .eq(UserMute::getMuterId, userId)
                .orderByDesc(UserMute::getCreatedAt));
    }
}
