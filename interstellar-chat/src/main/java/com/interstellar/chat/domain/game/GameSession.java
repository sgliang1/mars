package com.interstellar.chat.domain.game;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@TableName(value = "game_session", autoResultMap = true)
public class GameSession {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long conversationId;
    private String gameType;
    private Long creatorId;
    private String status;  // waiting, playing, finished

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> config;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> state;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
