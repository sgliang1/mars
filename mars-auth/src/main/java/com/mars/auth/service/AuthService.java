package com.mars.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mars.auth.dto.LoginRequest;
import com.mars.auth.dto.LoginResponse; // ✅ 必须导入这个
import com.mars.auth.entity.User;
import com.mars.auth.mapper.UserMapper;
import com.mars.common.Result;
import com.mars.common.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    @Autowired
    private UserMapper userMapper;

    // 密码加密器
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // ✅ 修改返回值泛型为 LoginResponse
    public Result<LoginResponse> login(LoginRequest request) {
        // 1. 查用户
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));

        if (user == null) {
            return Result.fail("用户不存在");
        }

        // 2. 校验密码 (兼容明文和密文)
        boolean isMatch = false;
        // 如果数据库里存的是 "123456" 这种短明文
        if (user.getPassword().length() < 50 && user.getPassword().equals(request.getPassword())) {
            isMatch = true;
        }
        // 如果数据库里是加密后的长字符串
        else if (passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            isMatch = true;
        }

        if (!isMatch) {
            return Result.fail("密码错误");
        }

        // 3. 生成 Token
        String token = JwtUtil.generateToken(user.getId(), user.getUsername());

        // ✅✅✅ 关键修复：
        // 之前 Result.success(token) 会被识别为 success(String msg)，导致 Token 变成了 msg 字段。
        // 现在改为返回 LoginResponse 对象，确保 Token 进入 data 字段。
        return Result.success(new LoginResponse(token, user.getId(), user.getUsername()));
    }

    // 注册逻辑
    public Result register(User user) {
        // 简单校验
        User exist = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, user.getUsername()));
        if (exist != null) return Result.fail("用户名已存在");

        // 注册时加密密码
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userMapper.insert(user);
        return Result.success("注册成功");
    }

    // 在 AuthService 类中添加
    public Result update(User user) {
        if (user.getId() == null) {
            return Result.fail("用户ID不能为空");
        }

        // 如果修改了用户名，需要检查是否与其他用户重复
        if (user.getUsername() != null) {
            User exist = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getUsername, user.getUsername())
                    .ne(User::getId, user.getId())); // 排除自己
            if (exist != null) {
                return Result.fail("用户名/昵h称已存在");
            }
        }

        // 仅更新非空字段
        userMapper.updateById(user);

        // 如果修改了用户名，最好返回新的 User 对象或 token，这里简单返回成功
        return Result.success("更新成功");
    }
}
