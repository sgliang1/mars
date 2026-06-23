package com.mars.post.domain.report;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("report")
public class Report {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long reporterId;
    private String targetType;   // post / comment / user
    private Long targetId;
    private String reason;       // spam / abuse / porn / false_info / other
    private String description;
    private Integer status;      // 0=待处理
    private LocalDateTime createdAt;
}
