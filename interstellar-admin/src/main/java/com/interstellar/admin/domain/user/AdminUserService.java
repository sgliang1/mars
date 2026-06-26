package com.interstellar.admin.domain.user;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AdminUserService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 注销用户（级联删除/软删，加事务保护）
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long userId, Long adminUserId) {
        jdbcTemplate.update("DELETE FROM user_relation WHERE follower_id = ? OR followed_id = ?", userId, userId);
        jdbcTemplate.update("DELETE FROM post_like WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM post_favorite WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM post_browse_history WHERE user_id = ?", userId);
        jdbcTemplate.update("UPDATE comment SET deleted_at = NOW(), deleted_by = ? WHERE user_id = ? AND deleted_at IS NULL", adminUserId, userId);
        jdbcTemplate.update("UPDATE post SET deleted_at = NOW(), deleted_by = ?, display_status = 2 WHERE user_id = ? AND deleted_at IS NULL", adminUserId, userId);
        jdbcTemplate.update("DELETE FROM user_profile WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM user WHERE id = ?", userId);
        log.info("用户注销完成: userId={}, adminUserId={}", userId, adminUserId);
    }

    /**
     * 封禁/解封用户（加事务保护：封禁时需同时隐藏帖子）
     */
    @Transactional(rollbackFor = Exception.class)
    public String updateUserStatus(Long userId, Integer status, int banDays) {
        if (status == 0) {
            String banUntil = null;
            if (banDays > 0) {
                banUntil = LocalDateTime.now().plusDays(banDays)
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            }
            int rows = jdbcTemplate.update(
                    "UPDATE user_profile SET status = 0, ban_until = ? WHERE user_id = ?",
                    banUntil, userId);
            if (rows == 0) {
                throw new IllegalArgumentException("用户不存在");
            }
            // 隐藏该用户所有已发布帖子
            jdbcTemplate.update(
                    "UPDATE post SET display_status = 2 WHERE user_id = ? AND display_status = 1 AND deleted_at IS NULL",
                    userId);
            return banDays > 0 ? "用户已封禁" + banDays + "天" : "用户已永久封禁";
        } else {
            int rows = jdbcTemplate.update(
                    "UPDATE user_profile SET status = 1, ban_until = NULL WHERE user_id = ?",
                    userId);
            if (rows == 0) {
                throw new IllegalArgumentException("用户不存在");
            }
            return "用户已解封";
        }
    }
}
