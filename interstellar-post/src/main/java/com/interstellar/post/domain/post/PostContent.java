package com.interstellar.post.domain.post;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("post_content")
public class PostContent {
    @TableId(type = IdType.INPUT) // ?????? Post.id ??
    private Long postId;
    private String content;
}