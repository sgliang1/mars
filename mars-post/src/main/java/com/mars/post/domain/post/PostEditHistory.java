package com.mars.post.domain.post;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("post_edit_history")
public class PostEditHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long postId;
    private String contentSnapshot;
    private LocalDateTime editedAt;
    private Long editorId;
}