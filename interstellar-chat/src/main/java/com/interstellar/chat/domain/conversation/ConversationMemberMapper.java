package com.interstellar.chat.domain.conversation;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interstellar.chat.domain.conversation.ConversationMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface ConversationMemberMapper extends BaseMapper<ConversationMember> {

    @Select("<script>" +
            "SELECT conversation_id AS conversationId, COUNT(*) AS cnt " +
            "FROM conversation_member WHERE conversation_id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            " GROUP BY conversation_id" +
            "</script>")
    List<Map<String, Object>> batchCountMembers(@Param("ids") List<Long> conversationIds);
}