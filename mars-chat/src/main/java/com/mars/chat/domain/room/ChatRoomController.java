package com.mars.chat.domain.room;

import com.mars.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/mars-chat/rooms")
public class ChatRoomController {

    @Autowired
    private ChatRoomService chatRoomService;

    @GetMapping
    public Result<List<Map<String, Object>>> list(@RequestHeader("X-User-Id") String userIdStr) {
        try {
            return Result.success(chatRoomService.listRooms(parseUserId(userIdStr)));
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("Failed to load rooms");
        }
    }

    @GetMapping("/{roomId}")
    public Result<Map<String, Object>> detail(@PathVariable("roomId") Long roomId,
                                              @RequestHeader("X-User-Id") String userIdStr) {
        try {
            return Result.success(chatRoomService.getRoom(parseUserId(userIdStr), roomId));
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("Failed to load room");
        }
    }

    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body,
                                              @RequestHeader("X-User-Id") String userIdStr,
                                              @RequestHeader(value = "X-Username", required = false) String username) {
        try {
            return Result.success(chatRoomService.createRoom(
                    parseUserId(userIdStr),
                    username,
                    value(body, "name"),
                    value(body, "description"),
                    value(body, "icon")
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("Failed to create room");
        }
    }

    @PostMapping("/{roomId}/join")
    public Result<Map<String, Object>> join(@PathVariable("roomId") Long roomId,
                                            @RequestHeader("X-User-Id") String userIdStr) {
        try {
            return Result.success(chatRoomService.joinRoom(parseUserId(userIdStr), roomId));
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("Failed to join room");
        }
    }

    @PostMapping("/{roomId}/leave")
    public Result<Map<String, Object>> leave(@PathVariable("roomId") Long roomId,
                                             @RequestHeader("X-User-Id") String userIdStr) {
        try {
            return Result.success(chatRoomService.leaveRoom(parseUserId(userIdStr), roomId));
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("Failed to leave room");
        }
    }

    private Long parseUserId(String userIdStr) {
        return Long.parseLong(userIdStr);
    }

    private String value(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? "" : v.toString();
    }
}