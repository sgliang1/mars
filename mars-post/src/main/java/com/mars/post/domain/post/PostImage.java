package com.mars.post.domain.post;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("post_image")
public class PostImage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long postId;
    private String url;
    private Integer sort; // 用于保证图片顺序
}
