package com.mars.common.mq;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 互动通知 MQ 消息体
 * 用于异步写入 notification 表
 */
public class NotificationMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 接收通知的用户 ID */
    private Long userId;
    /** 通知类别: interaction / system */
    private String category;
    /** 通知标题（通常是操作者用户名） */
    private String title;
    /** 通知内容 JSON */
    private String content;
    /** 来源类型: like / comment / follow */
    private String sourceType;
    /** 来源 ID（帖子 ID 等） */
    private String sourceId;
    /** 创建时间 */
    private LocalDateTime createdAt;

    public NotificationMessage() {}

    public NotificationMessage(Long userId, String category, String title,
                               String content, String sourceType, String sourceId) {
        this.userId = userId;
        this.category = category;
        this.title = title;
        this.content = content;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.createdAt = LocalDateTime.now();
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}