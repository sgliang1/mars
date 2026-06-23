package com.mars.user.domain.relation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mars.common.cache.CacheKeys;
import com.mars.common.cache.CacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class RelationSettingService {

    @Autowired
    private UserRelationSettingMapper settingMapper;

    @Autowired
    private CacheService cacheService;

    public UserRelationSetting getSetting(Long userId, Long targetUserId) {
        return settingMapper.selectOne(new LambdaQueryWrapper<UserRelationSetting>()
                .eq(UserRelationSetting::getUserId, userId)
                .eq(UserRelationSetting::getTargetUserId, targetUserId));
    }

    public UserRelationSetting getOrCreateSetting(Long userId, Long targetUserId) {
        UserRelationSetting setting = getSetting(userId, targetUserId);
        if (setting == null) {
            setting = new UserRelationSetting();
            setting.setUserId(userId);
            setting.setTargetUserId(targetUserId);
            setting.setPostVisible(1);
            setting.setProfileVisible(1);
            setting.setCanMessage(1);
            setting.setCreatedAt(LocalDateTime.now());
            setting.setUpdatedAt(LocalDateTime.now());
            settingMapper.insert(setting);
        }
        return setting;
    }

    public void updateSetting(Long userId, Long targetUserId, Integer postVisible,
                               Integer profileVisible, Integer canMessage) {
        UserRelationSetting setting = getOrCreateSetting(userId, targetUserId);
        if (postVisible != null) setting.setPostVisible(postVisible);
        if (profileVisible != null) setting.setProfileVisible(profileVisible);
        if (canMessage != null) setting.setCanMessage(canMessage);
        setting.setUpdatedAt(LocalDateTime.now());
        settingMapper.updateById(setting);

        // 同步消息权限到 Redis，供 mars-chat 跨服务读取
        if (canMessage != null) {
            String msgKey = CacheKeys.relationKey(CacheKeys.RELATION_MSG_SETTING, userId, targetUserId);
            cacheService.set(msgKey, String.valueOf(canMessage), CacheKeys.RELATION_MSG_SETTING_TTL);
        }
        // 同步帖子可见性到 Redis，供 mars-post 跨服务读取
        if (postVisible != null) {
            String postKey = CacheKeys.relationKey(CacheKeys.RELATION_POST_VISIBLE, userId, targetUserId);
            cacheService.set(postKey, String.valueOf(postVisible), CacheKeys.RELATION_POST_VISIBLE_TTL);
        }
        // 同步资料可见性到 Redis，供自身读取
        if (profileVisible != null) {
            String profileKey = CacheKeys.relationKey(CacheKeys.RELATION_PROFILE_VISIBLE, userId, targetUserId);
            cacheService.set(profileKey, String.valueOf(profileVisible), CacheKeys.RELATION_PROFILE_VISIBLE_TTL);
        }
    }

    /** 检查 viewer 是否能看到 targetUser 的帖子 */
    public boolean canViewPosts(Long viewerId, Long targetUserId) {
        if (viewerId.equals(targetUserId)) return true;
        UserRelationSetting setting = getSetting(targetUserId, viewerId);
        return setting == null || setting.getPostVisible() == 1;
    }

    /** 检查 viewer 是否能看到 targetUser 的详细资料 */
    public boolean canViewProfile(Long viewerId, Long targetUserId) {
        if (viewerId.equals(targetUserId)) return true;
        UserRelationSetting setting = getSetting(targetUserId, viewerId);
        return setting == null || setting.getProfileVisible() == 1;
    }

    /** 检查 sender 是否能给 targetUser 发私信 */
    public boolean canSendMessage(Long senderId, Long targetUserId) {
        if (senderId.equals(targetUserId)) return true;
        UserRelationSetting setting = getSetting(targetUserId, senderId);
        return setting == null || setting.getCanMessage() == 1;
    }
}