package com.interstellar.user.domain.level;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 等级经验值服务
 *
 * 经验值获取规则：
 *   发帖 +10 | 评论 +3 | 被点赞 +2 | 被收藏 +5 | 每日登录 +5
 *
 * 等级阈值：
 *   1(新手,0) | 2(初级,100) | 3(中级,500) | 4(高级,1500) | 5(资深,5000) | 6(大师,15000)
 */
@Service
class LevelService {

    @Autowired private JdbcTemplate jdbcTemplate;

    private static final int[] LEVEL_THRESHOLDS = {0, 0, 100, 500, 1500, 5000, 15000};
    private static final String[] LEVEL_NAMES = {"", "新手", "初级", "中级", "高级", "资深", "大师"};

    /**
     * 增加经验值并自动升级
     *
     * @param userId     用户ID
     * @param expChange  变动值（正数）
     * @param sourceType 来源：post / comment / liked / collected / login / system
     * @param sourceId   来源关联ID（可null）
     * @param description 描述
     */
    @Transactional
    public void addExp(Long userId, int expChange, String sourceType, Long sourceId, String description) {
        if (expChange <= 0) return;

        // 1. 写入经验值日志
        jdbcTemplate.update(
                "INSERT INTO user_exp_log (user_id, exp_change, source_type, source_id, description) VALUES (?, ?, ?, ?, ?)",
                userId, expChange, sourceType, sourceId, description);

        // 2. 累加经验值
        jdbcTemplate.update(
                "UPDATE user_profile SET exp_points = exp_points + ? WHERE user_id = ?",
                expChange, userId);

        // 3. 计算新等级
        Integer currentExp = jdbcTemplate.queryForObject(
                "SELECT exp_points FROM user_profile WHERE user_id = ?", Integer.class, userId);
        if (currentExp == null) return;

        int newLevel = 1;
        for (int i = LEVEL_THRESHOLDS.length - 1; i >= 1; i--) {
            if (currentExp >= LEVEL_THRESHOLDS[i]) {
                newLevel = i;
                break;
            }
        }

        Integer currentLevel = jdbcTemplate.queryForObject(
                "SELECT level FROM user_profile WHERE user_id = ?", Integer.class, userId);
        if (currentLevel != null && newLevel > currentLevel) {
            jdbcTemplate.update(
                    "UPDATE user_profile SET level = ?, level_name = ? WHERE user_id = ?",
                    newLevel, LEVEL_NAMES[newLevel], userId);
        }
    }

    /**
     * 扣除经验值（用于违规惩罚等）
     */
    @Transactional
    public void deductExp(Long userId, int expDeduct, String description) {
        if (expDeduct <= 0) return;
        jdbcTemplate.update(
                "UPDATE user_profile SET exp_points = GREATEST(exp_points - ?, 0) WHERE user_id = ?",
                expDeduct, userId);
        jdbcTemplate.update(
                "INSERT INTO user_exp_log (user_id, exp_change, source_type, description) VALUES (?, ?, ?, ?)",
                userId, -expDeduct, "system", description);
    }

    /**
     * 每日登录经验（每天限一次）
     */
    @Transactional
    public boolean addDailyLoginExp(Long userId) {
        LocalDate today = LocalDate.now();
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_exp_log WHERE user_id = ? AND source_type = 'login' AND DATE(create_time) = ?",
                Long.class, userId, today);
        if (count != null && count > 0) return false;

        addExp(userId, 5, "login", null, "每日登录奖励");
        return true;
    }

    /**
     * 获取下一级所需经验值
     */
    public int getNextLevelExp(int currentLevel) {
        if (currentLevel >= LEVEL_THRESHOLDS.length - 1) return -1; // 已满级
        return LEVEL_THRESHOLDS[currentLevel + 1];
    }
}