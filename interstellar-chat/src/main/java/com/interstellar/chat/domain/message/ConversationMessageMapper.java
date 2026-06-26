package com.interstellar.chat.domain.message;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interstellar.chat.domain.message.ConversationMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ConversationMessageMapper extends BaseMapper<ConversationMessage> {

    @Select("<script>" +
            "SELECT * FROM conversation_message WHERE conversation_id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            " ORDER BY created_at DESC, id DESC" +
            "</script>")
    List<ConversationMessage> batchLatestMessages(@Param("ids") List<Long> conversationIds);
}