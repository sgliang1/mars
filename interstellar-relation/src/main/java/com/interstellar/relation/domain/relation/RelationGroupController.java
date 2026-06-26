package com.interstellar.relation.domain.relation;

import com.interstellar.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/relation/groups")
@Tag(name = "关注分组", description = "关注分组标签管理")
public class RelationGroupController {

    @Autowired
    private RelationGroupService relationGroupService;

    @GetMapping
    @Operation(summary = "获取我的分组列表")
    public Result<List<RelationGroup>> listGroups(@RequestHeader("X-User-Id") String userIdStr) {
        Long userId = Long.parseLong(userIdStr);
        return Result.success(relationGroupService.listGroups(userId));
    }

    @PostMapping
    @Operation(summary = "创建分组")
    public Result<RelationGroup> createGroup(@RequestHeader("X-User-Id") String userIdStr,
                                              @RequestParam String name,
                                              @RequestParam(required = false) String icon) {
        Long userId = Long.parseLong(userIdStr);
        return Result.success(relationGroupService.createGroup(userId, name, icon));
    }

    @PutMapping("/{groupId}")
    @Operation(summary = "更新分组")
    public Result<String> updateGroup(@RequestHeader("X-User-Id") String userIdStr,
                                       @PathVariable Long groupId,
                                       @RequestParam(required = false) String name,
                                       @RequestParam(required = false) String icon,
                                       @RequestParam(required = false) Integer sortOrder) {
        Long userId = Long.parseLong(userIdStr);
        relationGroupService.updateGroup(groupId, userId, name, icon, sortOrder);
        return Result.successMessage("更新成功");
    }

    @DeleteMapping("/{groupId}")
    @Operation(summary = "删除分组")
    public Result<String> deleteGroup(@RequestHeader("X-User-Id") String userIdStr,
                                       @PathVariable Long groupId) {
        Long userId = Long.parseLong(userIdStr);
        relationGroupService.deleteGroup(groupId, userId);
        return Result.successMessage("删除成功");
    }

    @PostMapping("/{groupId}/members")
    @Operation(summary = "添加成员到分组")
    public Result<String> addMember(@RequestHeader("X-User-Id") String userIdStr,
                                     @PathVariable Long groupId,
                                     @RequestParam Long targetUserId) {
        Long userId = Long.parseLong(userIdStr);
        relationGroupService.addMemberByTargetUserId(groupId, userId, targetUserId);
        return Result.successMessage("添加成功");
    }

    @DeleteMapping("/{groupId}/members/{targetUserId}")
    @Operation(summary = "从分组移除成员")
    public Result<String> removeMember(@RequestHeader("X-User-Id") String userIdStr,
                                        @PathVariable Long groupId,
                                        @PathVariable Long targetUserId) {
        Long userId = Long.parseLong(userIdStr);
        relationGroupService.removeMemberByTargetUserId(groupId, userId, targetUserId);
        return Result.successMessage("移除成功");
    }

    @GetMapping("/{groupId}/following")
    @Operation(summary = "获取分组内的关注列表")
    public Result<List<Map<String, Object>>> getGroupFollowing(@RequestHeader("X-User-Id") String userIdStr,
                                                                @PathVariable Long groupId) {
        Long userId = Long.parseLong(userIdStr);
        return Result.success(relationGroupService.getGroupFollowingList(groupId, userId));
    }
}