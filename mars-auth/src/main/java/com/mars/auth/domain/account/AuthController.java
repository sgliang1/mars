package com.mars.auth.domain.account;

import com.mars.auth.domain.account.LoginRequest;
import com.mars.auth.domain.account.LoginResponse;
import com.mars.auth.domain.account.RegisterRequest;
import com.mars.auth.domain.account.User;
import com.mars.auth.domain.account.AuthService;
import com.mars.auth.domain.account.ProfileDashboardDTO;
import com.mars.auth.domain.dashboard.DashboardService;
import com.mars.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

@RestController
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private DashboardService dashboardService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/register")
    public Result register(@RequestBody RegisterRequest request) {
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(request.getPassword());
        user.setEmail(request.getEmail());
        
        return authService.register(user);
    }

    // PUT /user - 更新用户基本信息
    @PutMapping("/user")
    public Result update(@RequestHeader("X-User-Id") String userIdStr,
                         @RequestBody UpdateUserRequest request) {
        Long userId = Long.parseLong(userIdStr);
        return authService.update(userId, request);
    }

    // 给前端 Flutter 我的页面调用的聚合接口
    @GetMapping("/dashboard/{userId}")
    public Result<ProfileDashboardDTO> getDashboard(@PathVariable("userId") Long userId,
                                                     HttpServletRequest request) {
        return dashboardService.getDashboard(userId, request);
    }

    // 更新完整个人资料（用户表 + user_profile 表）
    @PutMapping("/user/profile")
    public Result updateProfile(@RequestBody UpdateProfileRequest request) {
        return authService.updateProfile(request);
    }
}
