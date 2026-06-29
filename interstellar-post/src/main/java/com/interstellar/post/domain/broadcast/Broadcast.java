package com.interstellar.post.domain.broadcast;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("broadcast")
public class Broadcast {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String content;
    private String coverImage;
    private String linkType;    // none/url/post/topic
    private String linkValue;
    private String targetScope; // all/followers/level
    private String targetValue;
    private Integer status;     // 0=draft, 1=published, 2=expired
    private LocalDateTime publishTime;
    private LocalDateTime expireTime;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
