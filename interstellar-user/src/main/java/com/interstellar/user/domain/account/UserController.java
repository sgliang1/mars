package com.interstellar.user.domain.account;

import com.interstellar.common.Result;
import com.interstellar.user.domain.dashboard.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@Tag(name = "用户", description = "用户资料与仪表盘")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/user/search")
    @Operation(summary = "搜索用户", description = "按用户名或昵称模糊搜索")
    public Result<List<Map<String, Object>>> searchUsers(
            @RequestParam String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader(value = "X-User-Id", required = false) String userIdStr) {

        int offset = (page - 1) * size;
        String likePattern = "%" + q + "%";

        List<Map<String, Object>> users = jdbcTemplate.queryForList(
                "SELECT u.id, u.username, p.nickname, p.avatar_url, p.bio, p.follower_count " +
                "FROM `user` u LEFT JOIN user_profile p ON u.id = p.user_id " +
                "WHERE u.username LIKE ? OR p.nickname LIKE ? " +
                "ORDER BY p.follower_count DESC " +
                "LIMIT ? OFFSET ?",
                likePattern, likePattern, size, offset);

        return Result.success(users);
    }

    @PutMapping("/user")
    @Operation(summary = "更新用户信息")
    public Result update(@RequestHeader("X-User-Id") String userIdStr,
                         @Valid @RequestBody UpdateUserRequest request) {
        Long userId = Long.parseLong(userIdStr);
        return userService.update(userId, request);
    }

    @GetMapping("/dashboard/{userId}")
    @Operation(summary = "用户仪表盘", description = "获取用户统计数据")
    public Result<ProfileDashboardDTO> getDashboard(@Parameter(description = "用户ID") @PathVariable("userId") Long userId,
                                                     HttpServletRequest request) {
        return dashboardService.getDashboard(userId, request);
    }

    @PutMapping("/user/profile")
    @Operation(summary = "更新个人资料")
    public Result updateProfile(@RequestHeader("X-User-Id") String userIdStr,
                                @Valid @RequestBody UpdateProfileRequest request) {
        request.setUserId(Long.parseLong(userIdStr));
        return userService.updateProfile(request);
    }

    @DeleteMapping("/user/account")
    @Operation(summary = "注销账号", description = "验证密码后注销当前用户账号")
    public Result deleteAccount(@RequestHeader("X-User-Id") String userIdStr,
                                @RequestBody Map<String, String> body) {
        Long userId = Long.parseLong(userIdStr);
        String password = body.get("password");
        if (password == null || password.isBlank()) {
            return Result.fail("请输入密码");
        }
        return userService.deleteAccount(userId, password);
    }
}