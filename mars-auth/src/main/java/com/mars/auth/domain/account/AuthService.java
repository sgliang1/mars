package com.mars.auth.domain.account;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mars.common.Result;
import com.mars.common.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserProfileMapper userProfileMapper;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public Result<LoginResponse> login(LoginRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));

        if (user == null) {
            return Result.fail("用户不存在");
        }

        boolean isMatch = false;
        if (user.getPassword().length() < 50 && user.getPassword().equals(request.getPassword())) {
            isMatch = true;
        } else if (passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            isMatch = true;
        }

        if (!isMatch) {
            return Result.fail("密码错误");
        }

        String token = JwtUtil.generateToken(user.getId(), user.getUsername());
        return Result.success(new LoginResponse(token, user.getId(), user.getUsername()));
    }

    public Result register(User user) {
        User exist = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, user.getUsername()));
        if (exist != null) return Result.fail("用户名已存在");

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userMapper.insert(user);
        return Result.successMessage("注册成功");
    }

    public Result update(Long userId, UpdateUserRequest request) {
        if (request.getUsername() != null) {
            User exist = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getUsername, request.getUsername())
                    .ne(User::getId, userId));
            if (exist != null) {
                return Result.fail("用户名已存在");
            }
        }

        User user = new User();
        user.setId(userId);
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        userMapper.updateById(user);

        return Result.successMessage("更新成功");
    }

    public Result updateProfile(UpdateProfileRequest request) {
        if (request.getUserId() == null) {
            return Result.fail("用户ID不能为空");
        }

        if (request.getUsername() != null && !request.getUsername().isBlank()) {
            User exist = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getUsername, request.getUsername())
                    .ne(User::getId, request.getUserId()));
            if (exist != null) {
                return Result.fail("昵称已被占用");
            }
            User user = new User();
            user.setId(request.getUserId());
            user.setUsername(request.getUsername());
            userMapper.updateById(user);
        }

        UserProfile profile = userProfileMapper.selectById(request.getUserId());
        final boolean isNewProfile = (profile == null);
        if (isNewProfile) {
            profile = new UserProfile();
            profile.setUserId(request.getUserId());
        }

        boolean needUpdateProfile = false;
        if (request.getBio() != null) {
            profile.setBio(request.getBio());
            needUpdateProfile = true;
        }
        if (request.getAvatarUrl() != null) {
            profile.setAvatarUrl(request.getAvatarUrl());
            needUpdateProfile = true;
        }
        if (request.getGender() != null) {
            profile.setGender(request.getGender());
            needUpdateProfile = true;
        }
        if (request.getBirthday() != null) {
            profile.setBirthday(request.getBirthday());
            needUpdateProfile = true;
        }

        if (needUpdateProfile) {
            if (isNewProfile) {
                userProfileMapper.insert(profile);
            } else {
                userProfileMapper.updateById(profile);
            }
        }

        return Result.successMessage("资料更新成功");
    }
}