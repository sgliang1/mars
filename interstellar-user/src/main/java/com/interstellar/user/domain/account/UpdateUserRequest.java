package com.interstellar.user.domain.account;

import lombok.Data;

@Data
public class UpdateUserRequest {
    private String username;
    private String email;
}