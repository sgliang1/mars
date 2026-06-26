package com.interstellar.chat.domain.room;

import com.interstellar.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/rooms")
@Tag(name = "聊天室", description = "公开聊天室管理")
public class ChatRoomController {

    @Autowired
    private ChatRoomService chatRoomService;

    @GetMapping
    @Operation(summary = "聊天室列表")
    public Result<List<Map<String, Object>>> list(@RequestHeader("X-User-Id") String userIdStr) {
        return Result.success(chatRoomService.listRooms(Long.parseLong(userIdStr)));
    }

    @GetMapping("/{roomId}")
    @Operation(summary = "聊天室详情")
    public Result<Map<String, Object>> detail(@Parameter(description = "房间ID") @PathVariable("roomId") Long roomId,
                                              @RequestHeader("X-User-Id") String userIdStr) {
        return Result.success(chatRoomService.getRoom(Long.parseLong(userIdStr), roomId));
    }

    @PostMapping
    @Operation(summary = "创建聊天室")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body,
                                              @RequestHeader("X-User-Id") String userIdStr,
                                              @RequestHeader(value = "X-Username", required = false) String username) {
        return Result.success(chatRoomService.createRoom(
                Long.parseLong(userIdStr),
                username,
                value(body, "name"),
                value(body, "description"),
                value(body, "icon")
        ));
    }

    @PostMapping("/{roomId}/join")
    @Operation(summary = "加入聊天室")
    public Result<Map<String, Object>> join(@Parameter(description = "房间ID") @PathVariable("roomId") Long roomId,
                                            @RequestHeader("X-User-Id") String userIdStr) {
        return Result.success(chatRoomService.joinRoom(Long.parseLong(userIdStr), roomId));
    }

    @PostMapping("/{roomId}/leave")
    @Operation(summary = "离开聊天室")
    public Result<Map<String, Object>> leave(@Parameter(description = "房间ID") @PathVariable("roomId") Long roomId,
                                             @RequestHeader("X-User-Id") String userIdStr) {
        return Result.success(chatRoomService.leaveRoom(Long.parseLong(userIdStr), roomId));
    }

    private String value(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? "" : v.toString();
    }
}