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
@Tag(name = "聊天室/俱乐部", description = "公开聊天室与俱乐部管理")
public class ChatRoomController {

    @Autowired
    private ChatRoomService chatRoomService;

    // ==================== 基础 CRUD ====================

    @GetMapping
    @Operation(summary = "列表")
    public Result<List<Map<String, Object>>> list(@RequestHeader("X-User-Id") String userIdStr,
                                                   @RequestParam(value = "planet", required = false) String planet,
                                                   @RequestParam(value = "clubsOnly", required = false, defaultValue = "false") boolean clubsOnly) {
        return Result.success(chatRoomService.listRooms(Long.parseLong(userIdStr), planet, clubsOnly));
    }

    @GetMapping("/{roomId}")
    @Operation(summary = "详情")
    public Result<Map<String, Object>> detail(@PathVariable("roomId") Long roomId,
                                              @RequestHeader("X-User-Id") String userIdStr) {
        return Result.success(chatRoomService.getRoom(Long.parseLong(userIdStr), roomId));
    }

    @PostMapping
    @Operation(summary = "创建")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body,
                                              @RequestHeader("X-User-Id") String userIdStr,
                                              @RequestHeader(value = "X-Username", required = false) String username) {
        Integer discoverable = body.get("discoverable") instanceof Number
                ? ((Number) body.get("discoverable")).intValue() : null;
        return Result.success(chatRoomService.createRoom(
                Long.parseLong(userIdStr), username,
                value(body, "name"), value(body, "description"), value(body, "icon"),
                value(body, "type"), value(body, "planet"),
                discoverable, value(body, "joinMode")));
    }

    @PutMapping("/{roomId}")
    @Operation(summary = "更新信息")
    public Result<Map<String, Object>> update(@PathVariable("roomId") Long roomId,
                                              @RequestBody Map<String, Object> body,
                                              @RequestHeader("X-User-Id") String userIdStr) {
        Integer discoverable = body.get("discoverable") instanceof Number
                ? ((Number) body.get("discoverable")).intValue() : null;
        return Result.success(chatRoomService.updateRoom(
                Long.parseLong(userIdStr), roomId,
                value(body, "name"), value(body, "description"), value(body, "icon"),
                value(body, "type"), value(body, "planet"),
                discoverable, value(body, "joinMode")));
    }

    // ==================== 加入/退出 ====================

    @PostMapping("/{roomId}/join")
    @Operation(summary = "加入（根据 joinMode 自动处理）")
    public Result<Map<String, Object>> join(@PathVariable("roomId") Long roomId,
                                            @RequestHeader("X-User-Id") String userIdStr) {
        return Result.success(chatRoomService.joinRoom(Long.parseLong(userIdStr), roomId));
    }

    @PostMapping("/{roomId}/leave")
    @Operation(summary = "退出")
    public Result<Map<String, Object>> leave(@PathVariable("roomId") Long roomId,
                                             @RequestHeader("X-User-Id") String userIdStr) {
        return Result.success(chatRoomService.leaveRoom(Long.parseLong(userIdStr), roomId));
    }

    // ==================== 审批 ====================

    @GetMapping("/{roomId}/join-requests")
    @Operation(summary = "待审批列表")
    public Result<List<Map<String, Object>>> joinRequests(@PathVariable("roomId") Long roomId) {
        return Result.success(chatRoomService.listJoinRequests(roomId));
    }

    @PostMapping("/{roomId}/approve/{targetUserId}")
    @Operation(summary = "审批通过")
    public Result<Map<String, Object>> approve(@PathVariable("roomId") Long roomId,
                                               @PathVariable("targetUserId") Long targetUserId,
                                               @RequestHeader("X-User-Id") String userIdStr) {
        return Result.success(chatRoomService.approveJoin(Long.parseLong(userIdStr), roomId, targetUserId));
    }

    @PostMapping("/{roomId}/reject/{targetUserId}")
    @Operation(summary = "审批拒绝")
    public Result<String> reject(@PathVariable("roomId") Long roomId,
                                 @PathVariable("targetUserId") Long targetUserId,
                                 @RequestHeader("X-User-Id") String userIdStr) {
        chatRoomService.rejectJoin(Long.parseLong(userIdStr), roomId, targetUserId);
        return Result.successMessage("已拒绝");
    }

    // ==================== 邀请 ====================

    @PostMapping("/{roomId}/invite/{targetUserId}")
    @Operation(summary = "邀请加入")
    public Result<String> invite(@PathVariable("roomId") Long roomId,
                                 @PathVariable("targetUserId") Long targetUserId,
                                 @RequestHeader("X-User-Id") String userIdStr) {
        chatRoomService.inviteUser(Long.parseLong(userIdStr), roomId, targetUserId);
        return Result.successMessage("已邀请");
    }

    // ==================== 成员管理 ====================

    @GetMapping("/{roomId}/members")
    @Operation(summary = "成员列表")
    public Result<List<Map<String, Object>>> members(@PathVariable("roomId") Long roomId) {
        return Result.success(chatRoomService.listMembers(roomId));
    }

    @PostMapping("/{roomId}/members/{targetUserId}/promote")
    @Operation(summary = "提升为副部长")
    public Result<String> promote(@PathVariable("roomId") Long roomId,
                                  @PathVariable("targetUserId") Long targetUserId,
                                  @RequestHeader("X-User-Id") String userIdStr) {
        chatRoomService.promoteMember(Long.parseLong(userIdStr), roomId, targetUserId);
        return Result.successMessage("已提升为副部长");
    }

    @PostMapping("/{roomId}/members/{targetUserId}/demote")
    @Operation(summary = "降为成员")
    public Result<String> demote(@PathVariable("roomId") Long roomId,
                                 @PathVariable("targetUserId") Long targetUserId,
                                 @RequestHeader("X-User-Id") String userIdStr) {
        chatRoomService.demoteMember(Long.parseLong(userIdStr), roomId, targetUserId);
        return Result.successMessage("已降为普通成员");
    }

    @PostMapping("/{roomId}/members/{targetUserId}/kick")
    @Operation(summary = "踢出成员")
    public Result<String> kick(@PathVariable("roomId") Long roomId,
                               @PathVariable("targetUserId") Long targetUserId,
                               @RequestHeader("X-User-Id") String userIdStr) {
        chatRoomService.kickMember(Long.parseLong(userIdStr), roomId, targetUserId);
        return Result.successMessage("已踢出");
    }

    @PostMapping("/{roomId}/members/{targetUserId}/ban")
    @Operation(summary = "禁言")
    public Result<String> ban(@PathVariable("roomId") Long roomId,
                              @PathVariable("targetUserId") Long targetUserId,
                              @RequestHeader("X-User-Id") String userIdStr) {
        chatRoomService.banMember(Long.parseLong(userIdStr), roomId, targetUserId);
        return Result.successMessage("已禁言");
    }

    @PostMapping("/{roomId}/members/{targetUserId}/unban")
    @Operation(summary = "解除禁言")
    public Result<String> unban(@PathVariable("roomId") Long roomId,
                                @PathVariable("targetUserId") Long targetUserId,
                                @RequestHeader("X-User-Id") String userIdStr) {
        chatRoomService.unbanMember(Long.parseLong(userIdStr), roomId, targetUserId);
        return Result.successMessage("已解除禁言");
    }

    // ==================== 部长操作 ====================

    @PostMapping("/{roomId}/transfer/{targetUserId}")
    @Operation(summary = "转让部长")
    public Result<String> transfer(@PathVariable("roomId") Long roomId,
                                   @PathVariable("targetUserId") Long targetUserId,
                                   @RequestHeader("X-User-Id") String userIdStr) {
        chatRoomService.transferPresident(Long.parseLong(userIdStr), roomId, targetUserId);
        return Result.successMessage("已转让部长");
    }

    @DeleteMapping("/{roomId}")
    @Operation(summary = "解散俱乐部")
    public Result<String> delete(@PathVariable("roomId") Long roomId,
                                 @RequestHeader("X-User-Id") String userIdStr) {
        chatRoomService.deleteClub(Long.parseLong(userIdStr), roomId);
        return Result.successMessage("已解散");
    }

    // ==================== 内部调用 ====================

    @GetMapping("/{roomId}/is-member")
    @Operation(summary = "检查是否是成员（内部）")
    public Result<Boolean> isMember(@PathVariable("roomId") Long roomId,
                                    @RequestParam("userId") Long userId) {
        return Result.success(chatRoomService.isMemberByRoom(roomId, userId));
    }

    @GetMapping("/user-club-name")
    @Operation(summary = "获取用户俱乐部名（内部）")
    public Result<String> getUserClubName(@RequestParam("userId") Long userId) {
        return Result.success(chatRoomService.getUserClubName(userId));
    }

    private String value(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? "" : v.toString();
    }
}
