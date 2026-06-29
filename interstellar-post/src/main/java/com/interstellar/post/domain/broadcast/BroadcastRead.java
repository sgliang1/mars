package com.interstellar.post.domain.broadcast;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("broadcast_read")
public class BroadcastRead {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long broadcastId;
    private LocalDateTime readAt;
}
