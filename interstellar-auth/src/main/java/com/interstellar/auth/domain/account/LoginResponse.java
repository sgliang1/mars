package com.interstellar.auth.domain.account;
import lombok.Data;
import lombok.AllArgsConstructor;

@Data
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private String refreshToken;
    private Long userId;
    private String username;
}
