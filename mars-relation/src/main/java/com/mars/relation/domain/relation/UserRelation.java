package com.mars.relation.domain.relation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_relation")
public class UserRelation {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("follower_id")
    private Long followerId;
    @TableField("followed_id")
    private Long followedId;
    private LocalDateTime createdAt;
    @TableField("source_type")
    private String sourceType;
    @TableField("source_id")
    private Long sourceId;
}