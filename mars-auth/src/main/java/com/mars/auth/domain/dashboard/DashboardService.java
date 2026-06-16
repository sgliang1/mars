package com.mars.auth.domain.dashboard;

import com.mars.api.PostFeignClient;
import com.mars.auth.domain.account.ProfileDashboardDTO;
import com.mars.auth.domain.account.User;
import com.mars.auth.domain.account.UserMapper;
import com.mars.auth.domain.account.UserProfile;
import com.mars.auth.domain.account.UserProfileMapper;
import com.mars.common.Result;
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

    public Result<ProfileDashboardDTO> getDashboard(Long userId, HttpServletRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.fail("用户不存在");
        }

        UserProfile profile = userProfileMapper.selectById(userId);
        if (profile == null) {
            return Result.fail("用户资料尚未初始化");
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
            log.warn("Feign 调用 mars-post 获取帖子数失败: {}", e.getMessage());
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
}