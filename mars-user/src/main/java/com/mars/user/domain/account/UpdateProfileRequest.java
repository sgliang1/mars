package com.mars.user.domain.account;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class UpdateProfileRequest {
    private Long userId;

    @Size(max = 32, message = "用户名长度不能超过 32 个字符")
    private String username;

    @Size(max = 500, message = "个人简介不能超过 500 个字符")
    private String bio;

    @Size(max = 500, message = "头像链接过长")
    private String avatarUrl;

    @Min(value = 0, message = "性别值无效")
    @Max(value = 2, message = "性别值无效")
    private Integer gender;

    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "生日格式应为 yyyy-MM-dd")
    private String birthday;
}
