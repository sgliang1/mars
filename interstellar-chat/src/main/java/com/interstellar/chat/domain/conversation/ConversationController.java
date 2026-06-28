package com.interstellar.chat.domain.conversation;

import com.interstellar.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/conversations")
@Tag(name = "会话", description = "私聊、群聊会话管理")
public class ConversationController {

    @Autowired
    private ConversationService conversationService;

    @GetMapping
    @Operation(summary = "会话列表")
    public Result<List<Map<String, Object>>> list(@RequestParam(value = "scope", required = false) String scope,
                                                  @RequestHeader("X-User-Id") String userIdStr,
                                                  @RequestParam(value = "page", defaultValue = "1") int page,
                                                  @RequestParam(value = "size", defaultValue = "50") int size) {
        Long userId = Long.parseLong(userIdStr);
        log.info("GET /conversations scope={} userId={} page={} size={}", scope, userId, page, size);
        var result = conversationService.listSummaries(userId, scope);
        log.info("GET /conversations success, returned {} items", result.size());
        return Result.success(result);
    }

    @GetMapping("/{conversationId}")
    @Operation(summary = "会话详情")
    public Result<Map<String, Object>> detail(@Parameter(description = "会话ID") @PathVariable("conversationId") Long conversationId,
                                              @RequestHeader("X-User-Id") String userIdStr) {
        return Result.success(conversationService.getSummary(Long.parseLong(userIdStr), conversationId));
    }

    @GetMapping("/{conversationId}/messages")
    @Operation(summary = "会话消息列表")
    public Result<List<Map<String, Object>>> messages(@Parameter(description = "会话ID") @PathVariable("conversationId") Long conversationId,
                                                      @RequestHeader("X-User-Id") String userIdStr) {
        return Result.success(conversationService.listMessages(Long.parseLong(userIdStr), conversationId));
    }

    @PostMapping("/direct")
    @Operation(summary = "创建私聊会话")
    public Result<Map<String, Object>> direct(@RequestBody Map<String, Object> body,
                                              @RequestHeader("X-User-Id") String userIdStr,
                                              @RequestHeader(value = "X-Username", required = false) String username) {
        return Result.success(conversationService.ensureDirectConversation(
                Long.parseLong(userIdStr),
                value(body, "targetUserId"),
                value(body, "username"),
                username
        ));
    }

    @PostMapping("/topic-group")
    public Result<Map<String, Object>> topicGroup(@RequestBody Map<String, Object> body,
                                                  @RequestHeader("X-User-Id") String userIdStr) {
        return Result.success(conversationService.ensureTopicGroupConversation(
                Long.parseLong(userIdStr),
                value(body, "topicSlug"),
                value(body, "title")
        ));
    }

    @PostMapping("/post-discuss")
    @Operation(summary = "获取/创建帖子实时讨论")
    public Result<Map<String, Object>> postDiscussion(@RequestBody Map<String, Object> body,
                                                      @RequestHeader("X-User-Id") String userIdStr) {
        return Result.success(conversationService.ensurePostDiscussion(
                Long.parseLong(userIdStr),
                value(body, "postId"),
                value(body, "title")
        ));
    }

    @PostMapping("/{conversationId}/read")
    public Result<Map<String, Object>> markRead(@PathVariable("conversationId") Long conversationId,
                                                @RequestHeader("X-User-Id") String userIdStr) {
        return Result.success(conversationService.markRead(Long.parseLong(userIdStr), conversationId));
    }

    @PostMapping("/{conversationId}/mute")
    public Result<Map<String, Object>> toggleMute(@PathVariable("conversationId") Long conversationId,
                                                  @RequestHeader("X-User-Id") String userIdStr) {
        return toggleState(userIdStr, conversationId, "mute");
    }

    @PostMapping("/{conversationId}/pin")
    public Result<Map<String, Object>> togglePin(@PathVariable("conversationId") Long conversationId,
                                                 @RequestHeader("X-User-Id") String userIdStr) {
        return toggleState(userIdStr, conversationId, "pin");
    }

    @PostMapping("/{conversationId}/archive")
    public Result<Map<String, Object>> toggleArchive(@PathVariable("conversationId") Long conversationId,
                                                     @RequestHeader("X-User-Id") String userIdStr) {
        return toggleState(userIdStr, conversationId, "archive");
    }

    @PostMapping("/{conversationId}/messages")
    @Operation(summary = "发送消息")
    public Result<Map<String, Object>> sendMessage(@Parameter(description = "会话ID") @PathVariable("conversationId") Long conversationId,
                                                   @RequestBody Map<String, Object> body,
                                                   @RequestHeader("X-User-Id") String userIdStr,
                                                   @RequestHeader(value = "X-Username", required = false) String username) {
        int messageType = 0;
        Object typeObj = body.get("messageType");
        if (typeObj instanceof Number n) {
            messageType = n.intValue();
        } else if (typeObj != null) {
            messageType = Integer.parseInt(typeObj.toString());
        }
        return Result.success(conversationService.sendMessage(
                Long.parseLong(userIdStr),
                conversationId,
                value(body, "content"),
                username,
                messageType
        ));
    }

    private Result<Map<String, Object>> toggleState(String userIdStr, Long conversationId, String action) {
        Long userId = Long.parseLong(userIdStr);
        if ("mute".equals(action)) {
            return Result.success(conversationService.toggleMute(userId, conversationId));
        }
        if ("pin".equals(action)) {
            return Result.success(conversationService.togglePin(userId, conversationId));
        }
        return Result.success(conversationService.toggleArchive(userId, conversationId));
    }

    private String value(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? "" : value.toString();
    }
}