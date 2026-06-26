package com.interstellar.post.domain.post;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("post_mention")
public class PostMention {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long postId;
    private Long commentId;
    private Long mentionedUserId;
    private LocalDateTime createTime;
}