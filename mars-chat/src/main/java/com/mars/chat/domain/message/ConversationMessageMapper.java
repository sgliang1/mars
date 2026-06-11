package com.mars.chat.domain.message;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mars.chat.domain.message.ConversationMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConversationMessageMapper extends BaseMapper<ConversationMessage> {
}
