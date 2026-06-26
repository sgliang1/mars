package com.interstellar.post.domain.topic;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("topic")
public class Topic {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String slug;
    private String title;
    private String summary;
    private String keywords;
    private String highlight;
    private String icon;
    private Integer sortOrder;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
