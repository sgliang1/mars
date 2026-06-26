package com.interstellar.user.domain.level;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user_exp_log")
public class UserExpLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Integer expChange;
    private String sourceType;  // post/comment/liked/collected/login/system
    private Long sourceId;
    private String description;
    private LocalDateTime createTime;
}