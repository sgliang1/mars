package com.mars.post.domain.post;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("post")
public class Post {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String username;
    private String title;
    private String summary;

    // �?删除或注释掉原来�?String images
    // private String images;

    // �?新增：存放图片列表，不映射到 post 表的字段
    @TableField(exist = false)
    private List<String> imageList;

    private Integer likeCount;
    private Integer commentCount;
    private LocalDateTime createTime;

    @TableField(exist = false)
    private boolean isLiked;
}
