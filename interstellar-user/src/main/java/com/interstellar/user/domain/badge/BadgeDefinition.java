package com.interstellar.user.domain.badge;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "badge_definition", autoResultMap = true)
public class BadgeDefinition {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private String iconUrl;
    private String rarity;      // common/uncommon/rare/epic/legendary
    private String category;    // achievement/activity/social/system
    private Integer maxSupply;  // null=无限
    private Integer currentSupply;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object conditions;
    private Integer status;     // 1=active, -1=retired
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
