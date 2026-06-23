package com.mars.auth.domain.account;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mars.common.Result;
import com.mars.common.model.User;
import com.mars.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class AuthService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private static final String REFRESH_TOKEN_PREFIX = "mars:auth:refresh:";
    private static final String REFRESH_FAMILY_PREFIX = "mars:auth:refresh_family:";
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(30);

    public Result<LoginResponse> login(LoginRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));

        if (user == null) {
            return Result.fail("用户不存在");
        }

        boolean isMatch = false;
        if (user.getPassword().length() < 50 && user.getPassword().equals(request.getPassword())) {
            isMatch = true;
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            userMapper.updateById(user);
        } else if (passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            isMatch = true;
        }

        if (!isMatch) {
            return Result.fail("密码错误");
        }

        String accessToken = JwtUtil.generateToken(user.getId(), user.getUsername());
        String refreshToken = JwtUtil.generateRefreshToken(user.getId(), user.getUsername());

        // 存储 refresh token 到 Redis（token family 机制）
        String familyId = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(REFRESH_FAMILY_PREFIX + user.getId(), familyId, REFRESH_TOKEN_TTL);
        redisTemplate.opsForValue().set(REFRESH_TOKEN_PREFIX + user.getId() + ":" + familyId, refreshToken, REFRESH_TOKEN_TTL);

        return Result.success(new LoginResponse(accessToken, refreshToken, user.getId(), user.getUsername()));
    }

    /**
     * 刷新令牌：验证 → 旋转（签发新的 access + refresh）→ 防重放
     */
    public Result<LoginResponse> refresh(String refreshToken) {
        try {
            // 1. 验证 token 类型
            if (!"refresh".equals(JwtUtil.getTokenType(refreshToken))) {
                return Result.fail("无效的刷新令牌");
            }

            Claims claims = JwtUtil.parseToken(refreshToken);
            Long userId = Long.parseLong(claims.get("userId").toString());
            String username = claims.get("username").toString();

            // 2. 检查 token family 是否有效
            String familyKey = REFRESH_FAMILY_PREFIX + userId;
            Object familyId = redisTemplate.opsForValue().get(familyKey);
            if (familyId == null) {
                return Result.fail("刷新令牌已失效，请重新登录");
            }

            // 3. 验证具体 token（防重放）
            String storedToken = (String) redisTemplate.opsForValue().get(
                    REFRESH_TOKEN_PREFIX + userId + ":" + familyId);
            if (!refreshToken.equals(storedToken)) {
                // 可疑重放攻击，吊销整个 family
                redisTemplate.delete(familyKey);
                redisTemplate.delete(REFRESH_TOKEN_PREFIX + userId + ":" + familyId);
                return Result.fail("刷新令牌异常，请重新登录");
            }

            // 4. 旋转：签发新的 access + refresh
            String newAccessToken = JwtUtil.generateToken(userId, username);
            String newRefreshToken = JwtUtil.generateRefreshToken(userId, username);

            String newFamilyId = UUID.randomUUID().toString();
            redisTemplate.opsForValue().set(familyKey, newFamilyId, REFRESH_TOKEN_TTL);
            redisTemplate.opsForValue().set(REFRESH_TOKEN_PREFIX + userId + ":" + newFamilyId,
                    newRefreshToken, REFRESH_TOKEN_TTL);
            redisTemplate.delete(REFRESH_TOKEN_PREFIX + userId + ":" + familyId);

            return Result.success(new LoginResponse(newAccessToken, newRefreshToken, userId, username));
        } catch (Exception e) {
            return Result.fail("刷新令牌无效或已过期，请重新登录");
        }
    }

    /**
     * 吊销用户的刷新令牌（注销/改密时调用）
     */
    public void revokeRefreshTokens(Long userId) {
        String familyKey = REFRESH_FAMILY_PREFIX + userId;
        Object familyId = redisTemplate.opsForValue().get(familyKey);
        if (familyId != null) {
            redisTemplate.delete(REFRESH_TOKEN_PREFIX + userId + ":" + familyId);
            redisTemplate.delete(familyKey);
        }
    }

    public Result register(User user) {
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

        // 改密后吊销所有 refresh token
        revokeRefreshTokens(userId);

        return Result.successMessage("密码修改成功");
    }
}
