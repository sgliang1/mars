package com.mars.user.domain.account;

import lombok.Data;

@Data
public class UpdateProfileRequest {
    private Long userId;
    private String username;
    private String bio;
    private String avatarUrl;
    private Integer gender;
    private String birthday;
}