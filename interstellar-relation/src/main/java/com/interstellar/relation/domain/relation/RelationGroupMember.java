package com.interstellar.relation.domain.relation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user_relation_group_member")
public class RelationGroupMember {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("group_id")
    private Long groupId;
    @TableField("relation_id")
    private Long relationId;
    @TableField("created_at")
    private LocalDateTime createdAt;
}