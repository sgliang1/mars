package com.interstellar.user.domain.account;

import lombok.Data;

@Data
public class ProfileDashboardDTO {
    private Long userId;
    private String username;
    private String avatarUrl;
    private String bio;
    private Integer gender;
    private String birthday;
    private String ipLocation;
    private Integer followingCount;
    private Integer followerCount;
    private Integer totalLikedCount;
    private Integer postCount;

    // 等级体系 & 信用分
    private Integer level;
    private Integer expPoints;
    private String levelName;
    private Integer creditScore;
    private Integer reputation;  // 声望分

    // 在线状态
    private String lastActiveAt;
    private boolean online;
}