package com.mars.auth.domain.account;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor  // 👈 加这�?
@AllArgsConstructor // 👈 加这�?
public class LoginRequest {
    private String username;
    private String password;
}
