package com.interstellar.post.domain.poll;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("poll_option")
public class PollOption {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long pollId;
    private String optionText;
    private Integer voteCount;
    private Integer sortOrder;
}