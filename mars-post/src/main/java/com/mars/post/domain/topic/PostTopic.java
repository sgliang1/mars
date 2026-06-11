package com.mars.post.domain.topic;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("post_topic")
public class PostTopic {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long postId;
    private Long topicId;
    private LocalDateTime createdAt;
}
