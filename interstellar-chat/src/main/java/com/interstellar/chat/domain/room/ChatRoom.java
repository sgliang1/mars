package com.interstellar.chat.domain.room;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_room")
public class ChatRoom {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long conversationId;
    private Long creatorId;
    private String creatorName;
    private String name;
    private String description;
    private String icon;
    private Integer maxMembers;
    private Integer memberCount;
    private Integer status; // 1=active, 0=closed
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}