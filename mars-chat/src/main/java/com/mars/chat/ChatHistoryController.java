package com.mars.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mars.chat.entity.ChatMessage;
import com.mars.chat.mapper.ChatMessageMapper;
import com.mars.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/mars-chat/history")
public class ChatHistoryController {

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @GetMapping("/recent")
    public Result<List<ChatMessage>> recent(
            @RequestParam(value = "limit", defaultValue = "40") Integer limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);

        List<ChatMessage> list = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .orderByDesc(ChatMessage::getCreateTime)
                        .last("limit " + safeLimit)
        );

        Collections.reverse(list);
        return Result.success(list);
    }
}
