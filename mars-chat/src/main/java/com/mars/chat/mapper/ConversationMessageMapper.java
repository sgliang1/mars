package com.mars.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mars.chat.entity.ConversationMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConversationMessageMapper extends BaseMapper<ConversationMessage> {
}
