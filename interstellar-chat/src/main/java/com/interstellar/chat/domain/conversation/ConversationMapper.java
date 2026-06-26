package com.interstellar.chat.domain.conversation;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interstellar.chat.domain.conversation.Conversation;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {
}
