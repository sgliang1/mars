package com.interstellar.chat.domain.room;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("club_join_request")
public class JoinRequest {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long roomId;
    private Long userId;
    private String status; // "pending" / "approved" / "rejected"
    private LocalDateTime createdAt;
}
