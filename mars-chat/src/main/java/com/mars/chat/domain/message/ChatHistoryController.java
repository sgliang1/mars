package com.mars.chat.domain.message;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mars.chat.domain.conversation.Conversation;
import com.mars.chat.domain.conversation.ConversationMapper;
import com.mars.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/mars-chat/history")
public class ChatHistoryController {

    private static final String PUBLIC_CHANNEL_BIZ_KEY = "public-lobby";

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private ConversationMessageMapper conversationMessageMapper;

    @Autowired
    private ConversationMapper conversationMapper;

    @GetMapping("/recent")
    public Result<List<Map<String, Object>>> recent(
            @RequestParam(value = "limit", defaultValue = "40") Integer limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);

        Conversation publicConv = conversationMapper.selectOne(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getBizKey, PUBLIC_CHANNEL_BIZ_KEY)
                .last("limit 1"));

        if (publicConv != null) {
            List<ConversationMessage> convMessages = conversationMessageMapper.selectList(
                    new LambdaQueryWrapper<ConversationMessage>()
                            .eq(ConversationMessage::getConversationId, publicConv.getId())
                            .orderByDesc(ConversationMessage::getCreatedAt)
                            .orderByDesc(ConversationMessage::getId)
                            .last("limit " + safeLimit));

            if (!convMessages.isEmpty()) {
                List<Map<String, Object>> result = new ArrayList<>();
                for (ConversationMessage msg : convMessages) {
                    result.add(toLegacyMap(msg));
                }
                Collections.reverse(result);
                return Result.success(result);
            }
        }

        List<ChatMessage> list = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .orderByDesc(ChatMessage::getCreateTime)
                        .last("limit " + safeLimit));

        Collections.reverse(list);
        List<Map<String, Object>> result = new ArrayList<>();
        for (ChatMessage msg : list) {
            result.add(toLegacyMap(msg));
        }
        return Result.success(result);
    }

    private Map<String, Object> toLegacyMap(ConversationMessage msg) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", msg.getId() == null ? "" : msg.getId().toString());
        data.put("senderId", msg.getSenderId() == null ? "" : msg.getSenderId().toString());
        data.put("senderName", msg.getSenderName());
        data.put("content", msg.getContent());
        data.put("createTime", msg.getCreatedAt() == null ? "" : msg.getCreatedAt().toString());
        data.put("type", msg.getMessageType() == null ? 0 : msg.getMessageType());
        return data;
    }

    private Map<String, Object> toLegacyMap(ChatMessage msg) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", msg.getId() == null ? "" : msg.getId().toString());
        data.put("senderId", msg.getSenderId() == null ? "" : msg.getSenderId().toString());
        data.put("senderName", msg.getSenderName());
        data.put("content", msg.getContent());
        data.put("createTime", msg.getCreateTime() == null ? "" : msg.getCreateTime().toString());
        data.put("type", msg.getType() == null ? 0 : msg.getType());
        return data;
    }
}