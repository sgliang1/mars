package com.mars.auth.domain.account;

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
    
    // 新增三大社交统计冗余字段
    private Integer followerCount;
    private Integer followingCount;
    private Integer totalLikedCount;
}
