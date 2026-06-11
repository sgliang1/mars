package com.mars.chat.domain.message;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("conversation_message")
public class ConversationMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long conversationId;
    private Long senderId;
    private String senderName;
    private String content;
    private Integer messageType;
    private String deliveryStatus;
    private LocalDateTime createdAt;
}
