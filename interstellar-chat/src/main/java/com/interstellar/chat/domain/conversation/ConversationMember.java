package com.interstellar.chat.domain.conversation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("conversation_member")
public class ConversationMember {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long conversationId;
    private Long userId;
    private String role;
    private String nickname;
    private String avatarUrl;
    private Integer unreadCount;
    private Boolean muted;
    private Boolean pinned;
    private Boolean archived;
    private LocalDateTime joinedAt;
    private LocalDateTime lastReadAt;
}
