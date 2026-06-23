package com.mars.post.domain.post;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("post_repost")
public class PostRepost {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long originalPostId;
    private String quoteContent;
    private LocalDateTime createTime;
}