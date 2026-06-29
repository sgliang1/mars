package com.interstellar.chat.domain.room;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("club_channel")
public class ClubChannel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long clubAId;
    private Long clubBId;
    private Long conversationId;
    private String status; // "pending" / "active" / "closed"
    private Long createdBy;
    private LocalDateTime createdAt;
}
