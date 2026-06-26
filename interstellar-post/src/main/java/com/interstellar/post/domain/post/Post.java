package com.interstellar.post.domain.post;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("post")
public class Post {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String username;
    private String title;
    private String summary;

    // �?删除或注释掉原来�?String images
    // private String images;

    // �?新增：存放图片列表，不映射到 post 表的字段
    @TableField(exist = false)
    private List<String> imageList;

    @TableField(exist = false)
    private String contentPreview;

    private Integer likeCount;
    private Integer commentCount;
    private Integer shareCount;
    private Integer viewCount;      // 浏览量
    private Integer isPinned;       // 是否置顶
    private Integer isFeatured;     // 是否加精
    private LocalDateTime pinnedAt; // 置顶时间
    private Integer visibility;     // 可见范围: 0公开 1仅粉丝 2仅好友 3仅自己
    private LocalDateTime scheduledAt; // 定时发布时间
    private LocalDateTime createTime;

    // Phase 4: 位置/定位
    private String locationName;    // 位置名称
    private java.math.BigDecimal latitude;   // 纬度
    private java.math.BigDecimal longitude;  // 经度

    // ========== 状态字段：双轨制 ==========
    private Integer auditStatus;       // 审核状态: 0-待审核, 1-机审通过, 2-人审通过, 3-人审驳回, 4-复审中(举报)
    private Integer displayStatus;     // 展示状态: 0-待发布, 1-已发布, 2-已下架
    private Integer prevAuditStatus;   // 被击穿前的审核状态(举报复审用)
    private Long lastAuditorId;        // 上一任审核人ID

    // 保留旧字段用于兼容
    private Integer status;            // 废弃：保留用于历史数据兼容

    private String reviewReason;       // 审核原因
    private Long reviewedBy;           // 审核人ID
    private LocalDateTime reviewedAt;  // 审核时间

    @TableField(exist = false)
    private boolean isLiked;

    // ========== 工作台扩展字段 ==========
    @TableField(exist = false)
    private String triggerReason;      // 入池原因：敏感词/被举报/定时巡查

    @TableField(exist = false)
    private String reviewerName;       // 审核人名称

    @TableField(exist = false)
    private String prevReviewerName;   // 上一任审核人名称

    // ========== 软删除 + 编辑历史 ==========
    private LocalDateTime deletedAt;   // 软删除时间，NULL 表示未删除
    private Integer editCount;         // 编辑次数
}
