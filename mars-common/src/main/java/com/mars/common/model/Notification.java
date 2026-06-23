package com.mars.common.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("notification")
public class Notification {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String category;
    private String title;
    private String content;
    private String sourceType;
    private String sourceId;

    /** 操作者用户ID（点赞/评论/关注的人） */
    private Long actorId;
    /** 关联帖子ID */
    private Long postId;

    private Integer readStatus;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
}