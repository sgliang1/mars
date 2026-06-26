package com.interstellar.user.domain.dashboard;

import com.interstellar.api.PostFeignClient;
import com.interstellar.common.Result;
import com.interstellar.common.cache.CacheKeys;
import com.interstellar.common.cache.CacheService;
import com.interstellar.user.domain.account.ProfileDashboardDTO;
import com.interstellar.common.model.User;
import com.interstellar.user.domain.account.UserMapper;
import com.interstellar.user.domain.account.UserProfile;
import com.interstellar.user.domain.account.UserProfileMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;

@Slf4j
@Service
public class DashboardService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserProfileMapper userProfileMapper;

    @Autowired(required = false)
    private PostFeignClient postFeignClient;

    @Autowired
    private CacheService cacheService;

    public Result<ProfileDashboardDTO> getDashboard(Long userId, HttpServletRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.fail("用户不存在");
        }

        UserProfile profile = userProfileMapper.selectById(userId);
        if (profile == null) {
            return Result.fail("用户资料尚未初始化");
        }

        // 检查资料可见性
        Long viewerId = extractViewerId(request);
        boolean isOwnProfile = viewerId != null && viewerId.equals(userId);
        if (!isOwnProfile && viewerId != null) {
            String profileVisibleKey = CacheKeys.relationKey(CacheKeys.RELATION_PROFILE_VISIBLE, userId, viewerId);
            Object profileVisible = cacheService.get(profileVisibleKey);
            if (profileVisible != null && "0".equals(profileVisible.toString())) {
                // 只返回基础公开信息
                ProfileDashboardDTO limited = new ProfileDashboardDTO();
                limited.setUserId(user.getId());
                limited.setUsername(user.getUsername());
                limited.setAvatarUrl(profile.getAvatarUrl());
                limited.setBio(profile.getBio());
                limited.setFollowingCount(0);
                limited.setFollowerCount(0);
                limited.setTotalLikedCount(0);
                limited.setPostCount(0);
                return Result.success(limited);
            }
        }

        ProfileDashboardDTO dto = new ProfileDashboardDTO();
        dto.setUserId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setAvatarUrl(profile.getAvatarUrl());
        dto.setBio(profile.getBio());
        dto.setGender(profile.getGender());
        dto.setBirthday(profile.getBirthday());
        dto.setIpLocation(resolveIpLocation(request));

        dto.setFollowingCount(profile.getFollowingCount() == null ? 0 : profile.getFollowingCount());
        dto.setFollowerCount(profile.getFollowerCount() == null ? 0 : profile.getFollowerCount());
        dto.setTotalLikedCount(profile.getTotalLikedCount() == null ? 0 : profile.getTotalLikedCount());
        dto.setPostCount(fetchPostCount(userId));

        // 等级体系 & 信用分
        dto.setLevel(profile.getLevel() != null ? profile.getLevel() : 1);
        dto.setExpPoints(profile.getExpPoints() != null ? profile.getExpPoints() : 0);
        dto.setLevelName(profile.getLevelName() != null ? profile.getLevelName() : "新手");
        dto.setCreditScore(profile.getCreditScore() != null ? profile.getCreditScore() : 100);

        return Result.success(dto);
    }

    private int fetchPostCount(Long userId) {
        if (postFeignClient == null) return 0;
        try {
            Result<Long> result = postFeignClient.getPostCount(userId);
            if (result != null && result.getData() != null) {
                return result.getData().intValue();
            }
        } catch (Exception e) {
            log.warn("Feign 调用 interstellar-post 获取帖子数失败: {}", e.getMessage());
        }
        return 0;
    }

    private String resolveIpLocation(HttpServletRequest request) {
        if (request == null) return "未知";
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }
        return ip != null && !ip.isBlank() ? ip : "未知";
    }

    private Long extractViewerId(HttpServletRequest request) {
        if (request == null) return null;
        String userIdStr = request.getHeader("X-User-Id");
        if (userIdStr == null || userIdStr.isBlank()) return null;
        try {
            return Long.parseLong(userIdStr.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}