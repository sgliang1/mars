package com.interstellar.user.domain.credit;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user_violation")
public class UserViolation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long reportId;
    private String violationType;  // spam/harassment/illegal/other
    private String severity;       // light/medium/heavy
    private Integer scoreDeducted;
    private String reason;
    private Long handledBy;
    private LocalDateTime createTime;
}