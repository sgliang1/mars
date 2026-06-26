package com.interstellar.user.domain.account;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interstellar.common.Result;
import com.interstellar.common.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserProfileMapper userProfileMapper;

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