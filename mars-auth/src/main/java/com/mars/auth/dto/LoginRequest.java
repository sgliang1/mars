package com.mars.auth.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor  // 👈 加这个
@AllArgsConstructor // 👈 加这个
public class LoginRequest {
    private String username;
    private String password;
}