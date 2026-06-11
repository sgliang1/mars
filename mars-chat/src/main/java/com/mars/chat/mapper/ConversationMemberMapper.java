package com.mars.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mars.chat.entity.ConversationMember;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConversationMemberMapper extends BaseMapper<ConversationMember> {
}
