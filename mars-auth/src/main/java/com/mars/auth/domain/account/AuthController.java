package com.mars.auth.domain.account;

import com.mars.auth.domain.account.LoginRequest;
import com.mars.auth.domain.account.LoginResponse;
import com.mars.auth.domain.account.RegisterRequest;
import com.mars.auth.domain.account.User;
import com.mars.auth.domain.account.AuthService;
import com.mars.auth.domain.account.ProfileDashboardDTO;
import com.mars.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
public class AuthController {

    @Autowired
    private AuthService authService;

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

    // �?AuthController 类中添加
    @PostMapping("/update")
    public Result update(@RequestBody User user) {
        return authService.update(user);
    }

    // 给前端 Flutter 我的页面调用的聚合接口
    @GetMapping("/dashboard/{userId}")
    public Result<ProfileDashboardDTO> getDashboard(@PathVariable("userId") Long userId) {
        return authService.getDashboard(userId);
    }
}
