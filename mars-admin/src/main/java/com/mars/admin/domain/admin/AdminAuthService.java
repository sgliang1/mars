package com.mars.admin.domain.admin;

import com.mars.common.Result;
import com.mars.common.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminAuthService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public Result<Map<String, Object>> login(String username, String password) {
        // 使用 JdbcTemplate 和 BeanPropertyRowMapper 替代 MyBatis-Plus
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

        // 更新最后登录时间
        jdbcTemplate.update("UPDATE admin_user SET last_login_at = ? WHERE id = ?", LocalDateTime.now(), admin.getId());

        // 生成 token，payload 中携带 role
        String token = JwtUtil.generateToken(admin.getId(), admin.getUsername(), admin.getRole());

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("userId", admin.getId());
        data.put("username", admin.getUsername());
        data.put("role", admin.getRole());
        return Result.success(data);
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