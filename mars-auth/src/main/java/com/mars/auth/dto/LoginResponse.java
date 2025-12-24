package com.mars.auth.dto;
import lombok.Data;
import lombok.AllArgsConstructor;

@Data
@AllArgsConstructor // 自动生成带参数的构造函数
public class LoginResponse {
    private String token;
    private Long userId;
    private String username;
}