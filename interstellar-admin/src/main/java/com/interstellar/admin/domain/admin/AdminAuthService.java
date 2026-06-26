package com.interstellar.admin.domain.admin;

import com.interstellar.common.Result;
import com.interstellar.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminAuthService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // 复用 interstellar-auth 的 Redis key 前缀，避免冲突（admin 用户 id 与 user 用户 id 可能重叠，
    // 但 admin_user 和 user 是独立表，refresh token 按各自前缀隔离）
    private static final String REFRESH_TOKEN_PREFIX = "interstellar:admin:refresh:";
    private static final String REFRESH_FAMILY_PREFIX = "interstellar:admin:refresh_family:";
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(30);

    public Result<Map<String, Object>> login(String username, String password) {
        List<AdminUser> list = jdbcTemplate.query(
                "SELECT * FROM admin_user WHERE username = ?",
                new BeanPropertyRowMapper<>(AdminUser.class),
                username
        );

        if (list.isEmpty()) {
            return Result.fail("管理员不存在");
        }

        AdminUser admin = list.get(0);

        if (admin.getStatus() == null || admin.getStatus() != 1) {
            return Result.fail("账号已被禁用");
        }
        if (!passwordEncoder.matches(password, admin.getPassword())) {
            return Result.fail("密码错误");
        }

        jdbcTemplate.update("UPDATE admin_user SET last_login_at = ? WHERE id = ?", LocalDateTime.now(), admin.getId());

        // 签发 access token（2h）和 refresh token（30d）
        String token = JwtUtil.generateToken(admin.getId(), admin.getUsername(), admin.getRole());
        String refreshToken = JwtUtil.generateRefreshToken(admin.getId(), admin.getUsername());

        // 存储 refresh token 到 Redis（token family 机制，与 interstellar-auth 保持一致）
        String familyId = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(REFRESH_FAMILY_PREFIX + admin.getId(), familyId, REFRESH_TOKEN_TTL);
        redisTemplate.opsForValue().set(REFRESH_TOKEN_PREFIX + admin.getId() + ":" + familyId, refreshToken, REFRESH_TOKEN_TTL);

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("refreshToken", refreshToken);
        data.put("userId", admin.getId());
        data.put("username", admin.getUsername());
        data.put("role", admin.getRole());
        return Result.success(data);
    }

    /**
     * 刷新令牌：验证 → 旋转（签发新的 access + refresh）→ 防重放
     * 复用 interstellar-auth 的 token family 设计
     */
    public Result<Map<String, Object>> refresh(String refreshToken) {
        try {
            // 1. 验证 token 类型
            if (!"refresh".equals(JwtUtil.getTokenType(refreshToken))) {
                return Result.fail("无效的刷新令牌");
            }

            Claims claims = JwtUtil.parseToken(refreshToken);
            Long userId = Long.parseLong(claims.get("userId").toString());
            String username = claims.get("username").toString();

            // 2. 查询管理员角色（refresh token 中不携带 role，需从数据库获取）
            List<Map<String, Object>> adminList = jdbcTemplate.queryForList(
                    "SELECT role FROM admin_user WHERE id = ?", userId);
            if (adminList.isEmpty()) {
                return Result.fail("管理员账号不存在");
            }
            String role = (String) adminList.get(0).get("role");

            // 3. 检查 token family 是否有效
            String familyKey = REFRESH_FAMILY_PREFIX + userId;
            Object familyId = redisTemplate.opsForValue().get(familyKey);
            if (familyId == null) {
                return Result.fail("刷新令牌已失效，请重新登录");
            }

            // 4. 验证具体 token（防重放）
            String storedToken = (String) redisTemplate.opsForValue().get(
                    REFRESH_TOKEN_PREFIX + userId + ":" + familyId);
            if (!refreshToken.equals(storedToken)) {
                // 可疑重放攻击，吊销整个 family
                redisTemplate.delete(familyKey);
                redisTemplate.delete(REFRESH_TOKEN_PREFIX + userId + ":" + familyId);
                return Result.fail("刷新令牌异常，请重新登录");
            }

            // 5. 旋转：签发新的 access + refresh
            String newAccessToken = JwtUtil.generateToken(userId, username, role);
            String newRefreshToken = JwtUtil.generateRefreshToken(userId, username);

            String newFamilyId = UUID.randomUUID().toString();
            redisTemplate.opsForValue().set(familyKey, newFamilyId, REFRESH_TOKEN_TTL);
            redisTemplate.opsForValue().set(REFRESH_TOKEN_PREFIX + userId + ":" + newFamilyId,
                    newRefreshToken, REFRESH_TOKEN_TTL);
            redisTemplate.delete(REFRESH_TOKEN_PREFIX + userId + ":" + familyId);

            Map<String, Object> data = new HashMap<>();
            data.put("token", newAccessToken);
            data.put("refreshToken", newRefreshToken);
            data.put("userId", userId);
            data.put("username", username);
            data.put("role", role);
            return Result.success(data);
        } catch (Exception e) {
            return Result.fail("刷新令牌无效或已过期，请重新登录");
        }
    }

    /**
     * 吊销管理员的刷新令牌（供改密、禁用等场景调用）
     */
    public void revokeRefreshTokens(Long userId) {
        String familyKey = REFRESH_FAMILY_PREFIX + userId;
        Object familyId = redisTemplate.opsForValue().get(familyKey);
        if (familyId != null) {
            redisTemplate.delete(REFRESH_TOKEN_PREFIX + userId + ":" + familyId);
            redisTemplate.delete(familyKey);
        }
    }

    public List<Map<String, Object>> listAdminUsers(int page, int size) {
        int offset = (page - 1) * size;
        return jdbcTemplate.queryForList(
                "SELECT id, username, role, status, created_at, last_login_at " +
                "FROM admin_user ORDER BY id DESC LIMIT ? OFFSET ?", size, offset);
    }

    public long countAdminUsers() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM admin_user", Long.class);
        return count != null ? count : 0;
    }

    public Result<Void> toggleAdminStatus(Long id) {
        List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "SELECT status FROM admin_user WHERE id = ?", id);
        if (list.isEmpty()) {
            return Result.fail("管理员不存在");
        }
        Integer current = (Integer) list.get(0).get("status");
        int newStatus = (current != null && current == 1) ? 0 : 1;
        jdbcTemplate.update("UPDATE admin_user SET status = ? WHERE id = ?", newStatus, id);
        // 禁用时吊销 refresh token，强制下线
        if (newStatus == 0) {
            revokeRefreshTokens(id);
        }
        return Result.successMessage(newStatus == 1 ? "已启用" : "已禁用");
    }

    public Result<Void> register(String username, String password, String role) {
        List<AdminUser> exist = jdbcTemplate.query(
                "SELECT id FROM admin_user WHERE username = ?",
                new BeanPropertyRowMapper<>(AdminUser.class),
                username
        );

        if (!exist.isEmpty()) {
            return Result.fail("用户名已存在");
        }

        // 使用 BCrypt 加密密码并插入
        jdbcTemplate.update(
                "INSERT INTO admin_user (username, password, role, status, created_at) VALUES (?, ?, ?, 1, ?)",
                username, passwordEncoder.encode(password), role != null ? role : "admin", LocalDateTime.now()
        );

        return Result.successMessage("注册成功");
    }
}