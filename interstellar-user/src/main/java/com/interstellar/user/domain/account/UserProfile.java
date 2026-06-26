package com.interstellar.user.domain.account;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_profile")
public class UserProfile {
    @TableId
    private Long userId;
    private String nickname;
    private String avatarUrl;
    private String bio;
    private Integer gender;
    private String birthday;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer followerCount;
    private Integer followingCount;
    private Integer totalLikedCount;

    // 隐私设置
    private Integer profileVisibility;  // 主页可见性: 0公开 1仅好友 2私密
    private Integer followingVisible;   // 关注列表可见
    private Integer followerVisible;    // 粉丝列表可见

    // Phase 4: 等级体系 & 信用分
    private Integer level;          // 用户等级 1-6
    private Integer expPoints;      // 经验值
    private String levelName;       // 等级名称
    private Integer creditScore;    // 信用分（满分100）

    // 主题偏好
    private String theme;           // dark / light
    private String themePreset;     // 预置主题 id，如 "default"
    private String themeAccent;     // 自定义主题色 hex，如 "ffff3333"
}