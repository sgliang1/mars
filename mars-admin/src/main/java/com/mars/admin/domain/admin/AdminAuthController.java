package com.mars.admin.domain.admin;

import com.mars.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/auth")
@Tag(name = "管理员认证与管理", description = "管理员登录、注册、列表、状态管理")
public class AdminAuthController {

    @Autowired
    private AdminAuthService adminAuthService;

    @PostMapping("/login")
    @Operation(summary = "管理员登录")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        if (username == null || password == null) {
            return Result.fail("用户名和密码不能为空");
        }
        return adminAuthService.login(username, password);
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新令牌", description = "使用 refreshToken 获取新的 accessToken 和 refreshToken")
    public Result<Map<String, Object>> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            return Result.fail("刷新令牌不能为空");
        }
        return adminAuthService.refresh(refreshToken);
    }

    @PostMapping("/register")
    @Operation(summary = "注册管理员", description = "仅限超级管理员操作")
    public Result<Void> register(
            @RequestHeader(value = "X-User-Role", required = false) String callerRole,
            @RequestHeader(value = "X-User-Id", required = false) String callerId,
            @RequestBody Map<String, String> body) {
        // 只有 super_admin 可以注册新管理员
        if (!"super_admin".equals(callerRole)) {
            return Result.fail("仅超级管理员可创建新管理员");
        }

        String username = body.get("username");
        String password = body.get("password");
        String role = body.get("role");
        if (username == null || password == null) {
            return Result.fail("用户名和密码不能为空");
        }
        // 不允许通过接口创建 super_admin
        if ("super_admin".equals(role)) {
            return Result.fail("不可通过接口创建超级管理员");
        }
        return adminAuthService.register(username, password, role);
    }

    @GetMapping("/users")
    @Operation(summary = "管理员列表", description = "分页查询管理员列表，不返回密码")
    public Result<Map<String, Object>> listUsers(
            @Parameter(description = "页码") @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(value = "size", defaultValue = "20") int size) {
        List<Map<String, Object>> records = adminAuthService.listAdminUsers(page, size);
        long total = adminAuthService.countAdminUsers();
        Map<String, Object> data = new HashMap<>();
        data.put("records", records);
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        return Result.success(data);
    }

    @PutMapping("/users/{id}/status")
    @Operation(summary = "切换管理员状态", description = "启用/禁用管理员账号")
    public Result<Void> toggleStatus(@PathVariable("id") Long id) {
        return adminAuthService.toggleAdminStatus(id);
    }
}