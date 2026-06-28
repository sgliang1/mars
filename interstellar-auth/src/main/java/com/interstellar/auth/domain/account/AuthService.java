package com.interstellar.auth.domain.account;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interstellar.common.Result;
import com.interstellar.common.model.User;
import com.interstellar.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * 认证服务：登录、注册、刷新令牌、修改密码
 */
@Slf4j
@Service
public class AuthService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private static final String REFRESH_TOKEN_PREFIX = "interstellar:auth:refresh:";
    private static final String REFRESH_FAMILY_PREFIX = "interstellar:auth:refresh_family:";
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(30);

    // ---- 登录防暴力破解 ----
    private static final String LOGIN_FAIL_PREFIX = "interstellar:auth:login_fail:";
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final Duration LOGIN_LOCK_TTL = Duration.ofMinutes(15);

    /**
     * 用户登录
     * - BCrypt 密码验证
     * - 连续失败 5 次锁定账号 15 分钟
     * - 明文密码兼容已移除（非 BCrypt 格式密码直接拒绝）
     */
    public Result<LoginResponse> login(LoginRequest request) {
        String username = request.getUsername();

        // 1. 检查账号是否被锁定
        String failKey = LOGIN_FAIL_PREFIX + username;
        Object failCountObj = redisTemplate.opsForValue().get(failKey);
        int failCount = failCountObj != null ? Integer.parseInt(failCountObj.toString()) : 0;
        if (failCount >= MAX_LOGIN_ATTEMPTS) {
            Long ttl = redisTemplate.getExpire(failKey);
            return Result.fail(423, "账号暂时锁定，请 " + (ttl != null && ttl > 0 ? ttl / 60 + 1 : 15) + " 分钟后重试");
        }

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));

        if (user == null) {
            recordLoginFailure(failKey);
            return Result.fail("用户不存在或密码错误");
        }

        // 2. 密码验证（仅支持 BCrypt 格式）
        String storedPassword = user.getPassword();
        if (storedPassword == null || storedPassword.length() < 50) {
            // 非 BCrypt 格式（可能是旧的明文密码），强制要求重置
            log.warn("用户 {} 的密码不是 BCrypt 格式，需重置", username);
            return Result.fail("密码格式异常，请联系管理员重置密码");
        }

        if (!passwordEncoder.matches(request.getPassword(), storedPassword)) {
            recordLoginFailure(failKey);
            return Result.fail("用户不存在或密码错误");
        }

        // 3. 登录成功，清除失败计数
        redisTemplate.delete(failKey);

        String accessToken = JwtUtil.generateToken(user.getId(), user.getUsername());
        String refreshToken = JwtUtil.generateRefreshToken(user.getId(), user.getUsername());

        // 存储 refresh token 到 Redis（token family 机制）
        String familyId = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(REFRESH_FAMILY_PREFIX + user.getId(), familyId, REFRESH_TOKEN_TTL);
        redisTemplate.opsForValue().set(REFRESH_TOKEN_PREFIX + user.getId() + ":" + familyId, refreshToken, REFRESH_TOKEN_TTL);

        return Result.success(new LoginResponse(accessToken, refreshToken, user.getId(), user.getUsername()));
    }

    /**
     * 记录登录失败，累计到上限后锁定
     */
    private void recordLoginFailure(String failKey) {
        Long count = redisTemplate.opsForValue().increment(failKey);
        if (count != null && count == 1) {
            // 首次失败时设置 TTL
            redisTemplate.expire(failKey, LOGIN_LOCK_TTL);
        }
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

    /**
     * 用户注册
     * <p>
     * 注意：User 参数必须由 Controller 层从 RegisterRequest DTO 手动映射，
     * 不可直接传入请求体（防止 mass assignment）。
     *
     * @param user 仅 username/password/email 字段被使用，其他字段忽略
     */
    public Result register(User user) {
        if (user.getUsername() == null || user.getUsername().length() < 3 || user.getUsername().length() > 20) {
            return Result.fail("用户名长度需为 3-20 个字符");
        }
        if (!user.getUsername().matches("^[a-zA-Z0-9_]{3,20}$")) {
            return Result.fail("用户名只能包含字母、数字和下划线");
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

    /**
     * 修改密码
     * - 仅支持 BCrypt 格式的旧密码验证
     * - 非 BCrypt 格式密码直接拒绝
     * - 修改后吊销所有 refresh token
     */
    public Result changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.fail("用户不存在");
        }

        String storedPassword = user.getPassword();
        if (storedPassword == null || storedPassword.length() < 50) {
            return Result.fail("密码格式异常，请联系管理员重置密码");
        }

        if (!passwordEncoder.matches(oldPassword, storedPassword)) {
            return Result.fail("旧密码错误");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);

        // 改密后吊销所有 refresh token
        revokeRefreshTokens(userId);

        return Result.successMessage("密码修改成功");
    }
}
