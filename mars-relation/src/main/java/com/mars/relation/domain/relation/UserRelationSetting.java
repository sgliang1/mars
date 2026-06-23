package com.mars.relation.domain.relation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user_relation_setting")
public class UserRelationSetting {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("user_id")
    private Long userId;
    @TableField("target_user_id")
    private Long targetUserId;
    @TableField("post_visible")
    private Integer postVisible;
    @TableField("profile_visible")
    private Integer profileVisible;
    @TableField("can_message")
    private Integer canMessage;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}