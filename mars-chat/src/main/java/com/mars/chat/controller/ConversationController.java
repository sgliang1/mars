package com.mars.chat.controller;

import com.mars.chat.service.ConversationService;
import com.mars.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/mars-chat/conversations")
public class ConversationController {

    @Autowired
    private ConversationService conversationService;

    @GetMapping
    public Result<List<Map<String, Object>>> list(@RequestParam(value = "scope", required = false) String scope,
                                                  @RequestHeader("X-User-Id") String userIdStr) {
        try {
            return Result.success(conversationService.listSummaries(parseUserId(userIdStr), scope));
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("Failed to load conversations");
        }
    }

    @GetMapping("/{conversationId}")
    public Result<Map<String, Object>> detail(@PathVariable Long conversationId,
                                              @RequestHeader("X-User-Id") String userIdStr) {
        try {
            return Result.success(conversationService.getSummary(parseUserId(userIdStr), conversationId));
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("Failed to load conversation");
        }
    }

    @GetMapping("/{conversationId}/messages")
    public Result<List<Map<String, Object>>> messages(@PathVariable Long conversationId,
                                                      @RequestHeader("X-User-Id") String userIdStr) {
        try {
            return Result.success(conversationService.listMessages(parseUserId(userIdStr), conversationId));
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("Failed to load messages");
        }
    }

    @PostMapping("/direct")
    public Result<Map<String, Object>> direct(@RequestBody Map<String, Object> body,
                                              @RequestHeader("X-User-Id") String userIdStr,
                                              @RequestHeader(value = "X-Username", required = false) String username) {
        try {
            return Result.success(conversationService.ensureDirectConversation(
                    parseUserId(userIdStr),
                    value(body, "targetUserId"),
                    value(body, "username"),
                    username
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("Failed to create direct conversation");
        }
    }

    @PostMapping("/topic-group")
    public Result<Map<String, Object>> topicGroup(@RequestBody Map<String, Object> body,
                                                  @RequestHeader("X-User-Id") String userIdStr) {
        try {
            return Result.success(conversationService.ensureTopicGroupConversation(
                    parseUserId(userIdStr),
                    value(body, "topicSlug"),
                    value(body, "title")
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("Failed to create topic conversation");
        }
    }

    @PostMapping("/{conversationId}/read")
    public Result<Map<String, Object>> markRead(@PathVariable Long conversationId,
                                                @RequestHeader("X-User-Id") String userIdStr) {
        try {
            return Result.success(conversationService.markRead(parseUserId(userIdStr), conversationId));
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("Failed to mark conversation read");
        }
    }

    @PostMapping("/{conversationId}/mute")
    public Result<Map<String, Object>> toggleMute(@PathVariable Long conversationId,
                                                  @RequestHeader("X-User-Id") String userIdStr) {
        return toggleState(userIdStr, conversationId, "mute");
    }

    @PostMapping("/{conversationId}/pin")
    public Result<Map<String, Object>> togglePin(@PathVariable Long conversationId,
                                                 @RequestHeader("X-User-Id") String userIdStr) {
        return toggleState(userIdStr, conversationId, "pin");
    }

    @PostMapping("/{conversationId}/archive")
    public Result<Map<String, Object>> toggleArchive(@PathVariable Long conversationId,
                                                     @RequestHeader("X-User-Id") String userIdStr) {
        return toggleState(userIdStr, conversationId, "archive");
    }

    @PostMapping("/{conversationId}/messages")
    public Result<Map<String, Object>> sendMessage(@PathVariable Long conversationId,
                                                   @RequestBody Map<String, Object> body,
                                                   @RequestHeader("X-User-Id") String userIdStr,
                                                   @RequestHeader(value = "X-Username", required = false) String username) {
        try {
            return Result.success(conversationService.sendMessage(
                    parseUserId(userIdStr),
                    conversationId,
                    value(body, "content"),
                    username
            ));
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("Failed to send message");
        }
    }

    private Result<Map<String, Object>> toggleState(String userIdStr, Long conversationId, String action) {
        try {
            Long userId = parseUserId(userIdStr);
            if ("mute".equals(action)) {
                return Result.success(conversationService.toggleMute(userId, conversationId));
            }
            if ("pin".equals(action)) {
                return Result.success(conversationService.togglePin(userId, conversationId));
            }
            return Result.success(conversationService.toggleArchive(userId, conversationId));
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("Failed to update conversation");
        }
    }

    private Long parseUserId(String userIdStr) {
        return Long.parseLong(userIdStr);
    }

    private String value(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? "" : value.toString();
    }
}
