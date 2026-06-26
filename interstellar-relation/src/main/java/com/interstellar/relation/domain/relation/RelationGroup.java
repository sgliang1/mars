package com.interstellar.relation.domain.relation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user_relation_group")
public class RelationGroup {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("user_id")
    private Long userId;
    private String name;
    private String icon;
    @TableField("sort_order")
    private Integer sortOrder;
    @TableField("created_at")
    private LocalDateTime createdAt;
}