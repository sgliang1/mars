package com.interstellar.user.domain.account;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interstellar.common.Result;
import com.interstellar.common.model.User;
import com.interstellar.common.push.DeviceTokenMapper;
import com.interstellar.common.push.DeviceToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;

@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserProfileMapper userProfileMapper;

    @Autowired
    private DeviceTokenMapper deviceTokenMapper;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

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
            // 自动计算是否未成年
            try {
                LocalDate birthDate = LocalDate.parse(request.getBirthday());
                int age = Period.between(birthDate, LocalDate.now()).getYears();
                profile.setIsMinor(age < 18 ? 1 : 0);
            } catch (Exception ignored) {
                // 日期格式不合法，不影响主流程
            }
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

    @Transactional
    public Result deleteAccount(Long userId, String password) {
        // 验证密码
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.fail("用户不存在");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            return Result.fail("密码错误");
        }

        // 更新 user_profile: 标记删除状态，清理敏感信息
        UserProfile profile = userProfileMapper.selectById(userId);
        if (profile == null) {
            profile = new UserProfile();
            profile.setUserId(userId);
        }
        profile.setStatus(-1);
        profile.setNickname("已注销用户");
        profile.setBio("");
        profile.setAvatarUrl("");
        if (userMapper.selectById(userId) != null) {
            userProfileMapper.insertOrUpdate(profile);
        }

        // 清除设备令牌
        deviceTokenMapper.delete(
                new LambdaQueryWrapper<DeviceToken>()
                        .eq(DeviceToken::getUserId, userId));

        return Result.successMessage("账号已注销");
    }
}