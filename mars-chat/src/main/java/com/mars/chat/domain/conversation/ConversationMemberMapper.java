package com.mars.chat.domain.conversation;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mars.chat.domain.conversation.ConversationMember;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConversationMemberMapper extends BaseMapper<ConversationMember> {
}
