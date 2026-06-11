package com.mars.post.domain.post;

import lombok.Data;

@Data
public class PostDTO {
    private String title;
    private String content; // 正文
    private String images;  // 图片URL，逗号分隔
}
