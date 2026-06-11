package com.mars.auth.domain.account;
import lombok.Data;
import lombok.AllArgsConstructor;

@Data
@AllArgsConstructor // 自动生成带参数的构造函�?
public class LoginResponse {
    private String token;
    private Long userId;
    private String username;
}
