package com.mars.post.domain.poll;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("poll_vote")
public class PollVote {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long pollId;
    private Long optionId;
    private Long userId;
    private LocalDateTime createTime;
}