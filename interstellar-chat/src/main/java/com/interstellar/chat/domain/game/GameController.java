package com.interstellar.chat.domain.game;

import com.interstellar.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/games")
@Tag(name = "小游戏", description = "谁是卧底等群聊小游戏")
public class GameController {

    @Autowired
    private GameService gameService;

    @PostMapping
    @Operation(summary = "创建游戏")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body,
                                              @RequestHeader("X-User-Id") String userIdStr,
                                              @RequestHeader(value = "X-Username", required = false) String username) {
        Long conversationId = Long.parseLong(body.get("conversationId").toString());
        String civilianWord = value(body, "civilianWord");
        String spyWord = value(body, "spyWord");
        int spyCount = body.get("spyCount") instanceof Number n ? n.intValue() : 1;
        int maxPlayers = body.get("maxPlayers") instanceof Number n ? n.intValue() : 8;

        return Result.success(gameService.createGame(
                Long.parseLong(userIdStr), username, conversationId,
                civilianWord, spyWord, spyCount, maxPlayers));
    }

    @PostMapping("/{gameId}/join")
    @Operation(summary = "加入游戏")
    public Result<Map<String, Object>> join(@Parameter @PathVariable("gameId") Long gameId,
                                            @RequestHeader("X-User-Id") String userIdStr,
                                            @RequestHeader(value = "X-Username", required = false) String username) {
        return Result.success(gameService.joinGame(Long.parseLong(userIdStr), username, gameId));
    }

    @PostMapping("/{gameId}/start")
    @Operation(summary = "开始游戏")
    public Result<Map<String, Object>> start(@Parameter @PathVariable("gameId") Long gameId,
                                             @RequestHeader("X-User-Id") String userIdStr) {
        return Result.success(gameService.startGame(Long.parseLong(userIdStr), gameId));
    }

    @PostMapping("/{gameId}/describe")
    @Operation(summary = "提交描述")
    public Result<Map<String, Object>> describe(@Parameter @PathVariable("gameId") Long gameId,
                                                @RequestBody Map<String, Object> body,
                                                @RequestHeader("X-User-Id") String userIdStr,
                                                @RequestHeader(value = "X-Username", required = false) String username) {
        String text = value(body, "text");
        return Result.success(gameService.submitDescription(Long.parseLong(userIdStr), username, gameId, text));
    }

    @PostMapping("/{gameId}/vote")
    @Operation(summary = "投票淘汰")
    public Result<Map<String, Object>> vote(@Parameter @PathVariable("gameId") Long gameId,
                                            @RequestBody Map<String, Object> body,
                                            @RequestHeader("X-User-Id") String userIdStr,
                                            @RequestHeader(value = "X-Username", required = false) String username) {
        Long targetUserId = Long.parseLong(body.get("targetUserId").toString());
        return Result.success(gameService.submitVote(Long.parseLong(userIdStr), username, gameId, targetUserId));
    }

    @GetMapping("/{gameId}")
    @Operation(summary = "获取游戏状态")
    public Result<Map<String, Object>> state(@Parameter @PathVariable("gameId") Long gameId,
                                             @RequestHeader("X-User-Id") String userIdStr) {
        return Result.success(gameService.getGameState(gameId, Long.parseLong(userIdStr)));
    }

    private String value(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? "" : v.toString();
    }
}
