package com.mars.relation.domain.relation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user_relation_event")
public class UserRelationEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("user_id")
    private Long userId;
    @TableField("target_user_id")
    private Long targetUserId;
    @TableField("event_type")
    private String eventType;
    @TableField("created_at")
    private LocalDateTime createdAt;
}