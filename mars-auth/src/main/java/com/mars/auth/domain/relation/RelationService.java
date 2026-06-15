package com.mars.auth.domain.relation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mars.auth.domain.account.UserProfileMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class RelationService {

    @Autowired
    private UserRelationMapper userRelationMapper;

    @Autowired
    private UserProfileMapper userProfileMapper;

    public List<Map<String, Object>> getFollowingList(Long userId) {
        return userRelationMapper.selectFollowingList(userId);
    }

    public List<Map<String, Object>> getFollowerList(Long userId) {
        return userRelationMapper.selectFollowerList(userId);
    }

    public boolean isFollowing(Long followerId, Long followedId) {
        return userRelationMapper.isFollowing(followerId, followedId);
    }

    public List<Map<String, Object>> getMutualFriends(Long userId) {
        return userRelationMapper.selectMutualFriends(userId);
    }

    public boolean isFollowedBy(Long currentUserId, Long targetUserId) {
        return userRelationMapper.isFollowing(targetUserId, currentUserId);
    }

    @Transactional
    public void follow(Long followerId, Long followedId) {
        if (followerId.equals(followedId)) {
            throw new IllegalArgumentException("不能关注自己");
        }
        if (isFollowing(followerId, followedId)) {
            throw new IllegalArgumentException("已关注该用户");
        }

        UserRelation relation = new UserRelation();
        relation.setFollowerId(followerId);
        relation.setFollowedId(followedId);
        relation.setCreatedAt(LocalDateTime.now());
        userRelationMapper.insert(relation);

        // 关注者 following_count +1
        userProfileMapper.updateFollowingCount(followerId, 1);
        // 被关注者 follower_count +1
        userProfileMapper.updateFollowerCount(followedId, 1);
    }

    @Transactional
    public void unfollow(Long followerId, Long followedId) {
        LambdaQueryWrapper<UserRelation> wrapper = new LambdaQueryWrapper<UserRelation>()
                .eq(UserRelation::getFollowerId, followerId)
                .eq(UserRelation::getFollowedId, followedId);
        int deleted = userRelationMapper.delete(wrapper);
        if (deleted == 0) {
            throw new IllegalArgumentException("未关注该用户");
        }

        // 关注者 following_count -1（不低于0）
        userProfileMapper.updateFollowingCount(followerId, -1);
        // 被关注者 follower_count -1（不低于0）
        userProfileMapper.updateFollowerCount(followedId, -1);
    }
}