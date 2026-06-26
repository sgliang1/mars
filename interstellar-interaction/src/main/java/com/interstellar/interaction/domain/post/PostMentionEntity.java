package com.interstellar.interaction.domain.post;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 帖子 @提及 记录（只读副本，共享数据库）
 */
@Data
@TableName("post_mention")
public class PostMentionEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long postId;
    private Long commentId;
    private Long mentionedUserId;
    private LocalDateTime createTime;
}
