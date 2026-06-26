package com.interstellar.chat.domain.message;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interstellar.chat.domain.message.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
