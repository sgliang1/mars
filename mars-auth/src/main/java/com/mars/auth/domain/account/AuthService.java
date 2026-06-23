package com.mars.auth.domain.account;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mars.common.Result;
import com.mars.common.model.User;
import com.mars.common.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    @Autowired
    private UserMapper userMapper;

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
            // 明文密码登录成功后自动升级为 BCrypt
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            userMapper.updateById(user);
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
        // 参数校验
        if (user.getUsername() == null || user.getUsername().length() < 3 || user.getUsername().length() > 20) {
            return Result.fail("用户名长度需为 3-20 个字符");
        }
        if (user.getPassword() == null || user.getPassword().length() < 6) {
            return Result.fail("密码长度不能少于 6 位");
        }
        if (user.getEmail() == null || !user.getEmail().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            return Result.fail("邮箱格式不正确");
        }

        User exist = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, user.getUsername()));
        if (exist != null) return Result.fail("用户名已存在");

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userMapper.insert(user);
        return Result.successMessage("注册成功");
    }

    public Result changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.fail("用户不存在");
        }

        // 验证旧密码（兼容明文和 BCrypt）
        boolean oldMatch = false;
        if (user.getPassword().length() < 50) {
            oldMatch = user.getPassword().equals(oldPassword);
        } else {
            oldMatch = passwordEncoder.matches(oldPassword, user.getPassword());
        }
        if (!oldMatch) {
            return Result.fail("旧密码错误");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
        return Result.successMessage("密码修改成功");
    }
}