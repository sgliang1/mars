package com.mars.auth.domain.account;

import com.mars.common.Result;
import com.mars.common.model.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@Tag(name = "认证", description = "登录与注册")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "用户名+密码登录，返回 JWT Token")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新令牌", description = "使用 refreshToken 获取新的 accessToken 和 refreshToken")
    public Result<LoginResponse> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            return Result.fail("刷新令牌不能为空");
        }
        return authService.refresh(refreshToken);
    }

    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "注册新用户账号")
    public Result register(@Valid @RequestBody RegisterRequest request) {
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(request.getPassword());
        user.setEmail(request.getEmail());
        return authService.register(user);
    }

    @PutMapping("/password")
    @Operation(summary = "修改密码")
    public Result changePassword(@RequestHeader("X-User-Id") String userIdStr,
                                 @Valid @RequestBody ChangePasswordRequest request) {
        Long userId = Long.parseLong(userIdStr);
        return authService.changePassword(userId, request.getOldPassword(), request.getNewPassword());
    }

    @Data
    public static class ChangePasswordRequest {
        @NotBlank(message = "旧密码不能为空")
        private String oldPassword;

        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, max = 64, message = "新密码长度需为 6-64 个字符")
        private String newPassword;
    }
}