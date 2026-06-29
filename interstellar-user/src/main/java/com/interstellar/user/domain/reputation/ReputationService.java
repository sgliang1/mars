package com.interstellar.user.domain.reputation;

import com.interstellar.common.cache.CacheKeys;
import com.interstellar.common.cache.CacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 声望服务
 *
 * 正向累积的声望分，与信用分（惩罚制）独立。
 *
 * 获取途径：
 *   发帖 +5 | 评论 +2 | 被点赞 +1 | 被收藏 +3
 *   连续签到7天 +10 | 举报核实 +5
 */
@Service
public class ReputationService {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private CacheService cacheService;

    private static final String[] REP_LEVELS = {"", "星际流浪者", "行星居民", "恒星使者", "星系守护者", "宇宙传奇"};

    @Transactional
    public void addReputation(Long userId, int amount, String sourceType, Long sourceId, String description) {
        if (amount <= 0) return;

        // 写入声望日志
        jdbcTemplate.update(
                "INSERT INTO user_reputation_log (user_id, rep_change, source_type, source_id, description) VALUES (?, ?, ?, ?, ?)",
                userId, amount, sourceType, sourceId, description);

        // 累加声望
        jdbcTemplate.update(
                "UPDATE user_profile SET reputation = reputation + ? WHERE user_id = ?",
                amount, userId);
    }

    public Map<String, Object> getReputation(Long userId) {
        Integer reputation = jdbcTemplate.queryForObject(
                "SELECT reputation FROM user_profile WHERE user_id = ?", Integer.class, userId);
        int score = reputation != null ? reputation : 0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reputation", score);
        result.put("level", getReputationLevel(score));
        result.put("levelIndex", getReputationLevelIndex(score));
        return result;
    }

    public String getReputationLevel(int score) {
        if (score >= 10000) return REP_LEVELS[5];
        if (score >= 5000) return REP_LEVELS[4];
        if (score >= 1500) return REP_LEVELS[3];
        if (score >= 500) return REP_LEVELS[2];
        if (score >= 100) return REP_LEVELS[1];
        return REP_LEVELS[0];
    }

    private int getReputationLevelIndex(int score) {
        if (score >= 10000) return 5;
        if (score >= 5000) return 4;
        if (score >= 1500) return 3;
        if (score >= 500) return 2;
        if (score >= 100) return 1;
        return 0;
    }
}
