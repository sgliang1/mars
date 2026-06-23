package com.mars.post.domain.comment;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("comment")
public class Comment {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long postId;
    private Long userId;
    private String username;
    private String content;
    private String imageUrl;
    private Integer likeCount;
    private String avatar;

    @TableField("parent_id")
    private Long parentId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    // ========== 软删除 ==========
    private LocalDateTime deletedAt;   // 软删除时间，NULL 表示未删除
    private Long deletedBy;            // 删除操作人（用户自己删=NULL，管理员删=admin_id）

    @TableField(exist = false)
    private List<Long> mentionUserIds; // @用户ID列表，不映射到数据库
}