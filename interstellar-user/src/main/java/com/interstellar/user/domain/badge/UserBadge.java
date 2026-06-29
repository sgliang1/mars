package com.interstellar.user.domain.badge;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_badge")
public class UserBadge {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long badgeId;
    private LocalDateTime awardedAt;
    private String source;     // system/checkin/level/activity/admin
    private Long sourceId;
}
