package com.mars.user.domain.relation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user_mute")
public class UserMute {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long muterId;
    private Long mutedId;
    private LocalDateTime createdAt;
}