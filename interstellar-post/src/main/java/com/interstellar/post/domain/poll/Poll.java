package com.interstellar.post.domain.poll;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("poll")
public class Poll {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long postId;
    private String question;
    private Integer isMultiple;
    private Integer totalVotes;
    private LocalDateTime expireAt;
    private Integer isDeleted;
    private LocalDateTime createTime;

    @TableField(exist = false)
    private List<PollOption> options;

    @TableField(exist = false)
    private Long userSelectedOptionId; // 当前用户已选的选项
}