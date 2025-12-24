package com.mars.post.entity;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("post_content")
public class PostContent {
    @TableId // 这里手动设置与 Post ID 一致
    private Long postId;
    private String content;
}