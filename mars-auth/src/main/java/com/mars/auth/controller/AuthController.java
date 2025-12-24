package com.mars.auth.controller;

import com.mars.auth.dto.LoginRequest;
import com.mars.auth.dto.LoginResponse;
import com.mars.auth.dto.RegisterRequest;
import com.mars.auth.entity.User;
import com.mars.auth.service.AuthService;
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
}