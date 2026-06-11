package com.mars.post.domain.post;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("post_browse_history")
public class PostBrowseHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long postId;
    private Long userId;
    private Integer viewCount;
    private LocalDateTime lastViewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
