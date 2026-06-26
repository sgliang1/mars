package com.interstellar.search.domain.post;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 帖子内容实体（只读副本，共享数据库）
 */
@Data
@TableName("post_content")
public class PostContent {
    @TableId(value = "post_id", type = IdType.INPUT)
    private Long postId;
    private String content;
}
