package com.mars.auth.domain.account;

import lombok.Data;

@Data
public class ProfileDashboardDTO {
    private Long userId;
    private String username;
    private String avatarUrl;
    private String bio;
    
    // 核心关注指标，与 Flutter 前端模型强绑定
    private Integer followingCount;
    private Integer followerCount;
    private Integer totalLikedCount;
    
    private Integer postCount; // 动态发帖数
}