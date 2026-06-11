package com.mars.auth.domain.account;

import lombok.Data;

@Data
public class RegisterRequest {
    private String username;
    private String password;
    private String email;
}
