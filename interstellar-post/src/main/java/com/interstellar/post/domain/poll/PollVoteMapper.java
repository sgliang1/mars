package com.interstellar.post.domain.poll;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
interface PollVoteMapper extends BaseMapper<PollVote> {
}