package com.interstellar.user.domain.credit;

import com.interstellar.common.cache.CacheKeys;
import com.interstellar.common.cache.CacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 信用分服务
 *
 * 初始 100 分
 * 举报核实扣分：轻度 -5 / 中度 -10 / 重度 -20
 * 限制阈值：<80 限流 / <60 禁言 / <30 封号
 * 每日自动恢复 +1（上限 100）
 */
@Service
class CreditService {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private CacheService cacheService;

    private static final int INITIAL_SCORE = 100;
    private static final int MAX_SCORE = 100;

    /**
     * 扣除信用分
     *
     * @param userId       用户ID
     * @param severity     轻重程度：light / medium / heavy
     * @param reason       原因
     * @param reportId     关联举报ID（可null）
     * @param handledBy    处理人ID
     */
    @Transactional
    public int deductCredit(Long userId, String severity, String reason, Long reportId, Long handledBy) {
        int deduction;
        switch (severity) {
            case "light":  deduction = 5;  break;
            case "medium": deduction = 10; break;
            case "heavy":  deduction = 20; break;
            default:       deduction = 5;
        }

        // 写入违规记录
        jdbcTemplate.update(
                "INSERT INTO user_violation (user_id, report_id, violation_type, severity, score_deducted, reason, handled_by) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)",
                userId, reportId, "other", severity, deduction, reason, handledBy);

        // 扣分
        jdbcTemplate.update(
                "UPDATE user_profile SET credit_score = GREATEST(credit_score - ?, 0) WHERE user_id = ?",
                deduction, userId);

        Integer newScore = jdbcTemplate.queryForObject(
                "SELECT credit_score FROM user_profile WHERE user_id = ?", Integer.class, userId);
        int score = newScore != null ? newScore : 0;

        // 同步信用分到 Redis，供 interstellar-post 跨服务读取
        cacheService.set(CacheKeys.key(CacheKeys.USER_CREDIT, userId), String.valueOf(score), CacheKeys.USER_CREDIT_TTL);

        return score;
    }

    /**
     * 获取信用等级描述
     */
    public String getCreditLevel(int score) {
        if (score >= 90) return "优秀";
        if (score >= 80) return "良好";
        if (score >= 60) return "一般";
        if (score >= 30) return "警告";
        return "严重";
    }

    /**
     * 获取当前限制状态
     */
    public String getRestriction(int score) {
        if (score >= 80) return "none";
        if (score >= 60) return "rate_limit";     // 发帖频率限制
        if (score >= 30) return "mute";           // 禁言
        return "ban";                              // 封号
    }

    /**
     * 每日定时任务：信用分自动恢复 +1（上限100）
     * 每天凌晨 2 点执行
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void dailyRecoverCredit() {
        int updated = jdbcTemplate.update(
                "UPDATE user_profile SET credit_score = LEAST(credit_score + 1, ?) WHERE credit_score < ?",
                MAX_SCORE, MAX_SCORE);
        if (updated > 0) {
            System.out.println("[信用分恢复] 已为 " + updated + " 位用户恢复信用分 +1");
        }
    }
}