package com.interstellar.interaction.domain.comment;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("comment")
public class Comment {
    @TableId(type = IdType.AUTO)
    private Long id;

    @NotNull(message = "帖子ID不能为空")
    private Long postId;
    private Long userId;
    private String username;

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 2000, message = "评论内容不能超过 2000 个字符")
    private String content;

    @Size(max = 500, message = "图片链接过长")
    private String imageUrl;
    private Integer likeCount;
    private String avatar;

    @TableField("parent_id")
    private Long parentId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    // ========== 软删除 ==========
    private LocalDateTime deletedAt;
    private Long deletedBy;

    @TableField(exist = false)
    @Size(max = 50, message = "@提及用户数不能超过 50 个")
    private List<Long> mentionUserIds;
}
