package com.mars.post.domain.comment;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("comment")
public class Comment {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long postId;
    private Long userId;
    private String username;
    private String content;
    private String imageUrl; // 评论图片
    
    @TableField("parent_id")
    private Long parentId; // 回复�?

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
