package com.mars.search.domain.post;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 帖子实体（只读副本，共享数据库）
 * 仅用于 mars-search 查询帖子数据以同步到 ES
 */
@Data
@TableName("post")
public class PostEntity {
    private Long id;
    private Long userId;
    private String username;
    private String title;
    private String summary;
    private Integer likeCount;
    private Integer commentCount;
    private Integer shareCount;
    private Integer viewCount;
    private Integer isPinned;
    private Integer isFeatured;
    private LocalDateTime pinnedAt;
    private Integer visibility;
    private LocalDateTime scheduledAt;
    private LocalDateTime createTime;
    private String locationName;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Integer auditStatus;
    private Integer displayStatus;
    private Integer prevAuditStatus;
    private Long lastAuditorId;
    private Integer status;
    private String reviewReason;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime deletedAt;
    private Integer editCount;
}
