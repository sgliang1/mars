package com.interstellar.user.domain.checkin;

import com.interstellar.user.domain.level.LevelService;
import com.interstellar.user.domain.reputation.ReputationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * 每日签到服务
 *
 * 连续签到规则：
 *   基础奖励 +5 EXP
 *   连续 7 天额外 +10 EXP
 *   连续 30 天额外 +50 EXP
 *   断签则连续天数重置为 1
 */
@Service
public class CheckinService {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private LevelService levelService;
    @Autowired private ReputationService reputationService;

    private static final int BASE_REWARD = 5;
    private static final int STREAK_7_BONUS = 10;
    private static final int STREAK_30_BONUS = 50;

    @Transactional
    public Map<String, Object> checkin(Long userId) {
        LocalDate today = LocalDate.now();

        // 检查今日是否已签到
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM daily_checkin WHERE user_id = ? AND checkin_date = ?",
                Long.class, userId, today);
        if (count != null && count > 0) {
            throw new IllegalArgumentException("今日已签到");
        }

        // 计算连续天数
        LocalDate yesterday = today.minusDays(1);
        Integer yesterdayStreak = jdbcTemplate.queryForObject(
                "SELECT streak_days FROM daily_checkin WHERE user_id = ? AND checkin_date = ?",
                Integer.class, userId, yesterday);
        int streak = (yesterdayStreak != null) ? yesterdayStreak + 1 : 1;

        // 计算奖励
        int reward = BASE_REWARD;
        if (streak >= 30) reward += STREAK_30_BONUS;
        else if (streak >= 7) reward += STREAK_7_BONUS;

        // 写入签到记录
        jdbcTemplate.update(
                "INSERT INTO daily_checkin (user_id, checkin_date, streak_days, reward_exp) VALUES (?, ?, ?, ?)",
                userId, today, streak, reward);

        // 发放经验值
        levelService.addExp(userId, reward, "checkin", null, "每日签到奖励 (连续" + streak + "天)");

        // 连续签到 7 天以上奖励声望 +10
        if (streak >= 7) {
            reputationService.addReputation(userId, 10, "checkin_streak", null,
                    "连续签到" + streak + "天奖励");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("checkedIn", true);
        result.put("streak", streak);
        result.put("reward", reward);
        return result;
    }

    public Map<String, Object> getCheckinStatus(Long userId) {
        LocalDate today = LocalDate.now();

        Long todayCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM daily_checkin WHERE user_id = ? AND checkin_date = ?",
                Long.class, userId, today);
        boolean todayChecked = todayCount != null && todayCount > 0;

        // 获取最近一次签到的连续天数
        Integer currentStreak = jdbcTemplate.queryForObject(
                "SELECT streak_days FROM daily_checkin WHERE user_id = ? ORDER BY checkin_date DESC LIMIT 1",
                Integer.class, userId);

        // 检查是否断签（最近签到不是今天也不是昨天）
        if (currentStreak != null && !todayChecked) {
            LocalDate lastDate = jdbcTemplate.queryForObject(
                    "SELECT checkin_date FROM daily_checkin WHERE user_id = ? ORDER BY checkin_date DESC LIMIT 1",
                    LocalDate.class, userId);
            if (lastDate != null && !lastDate.equals(today) && !lastDate.equals(today.minusDays(1))) {
                currentStreak = 0; // 已断签
            }
        }

        Long totalCheckins = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM daily_checkin WHERE user_id = ?",
                Long.class, userId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("todayChecked", todayChecked);
        result.put("streak", currentStreak != null ? currentStreak : 0);
        result.put("total", totalCheckins != null ? totalCheckins : 0);
        return result;
    }

    public List<Map<String, Object>> getCheckinCalendar(Long userId, int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.plusMonths(1).minusDays(1);

        List<Map<String, Object>> records = jdbcTemplate.queryForList(
                "SELECT checkin_date, streak_days FROM daily_checkin " +
                "WHERE user_id = ? AND checkin_date BETWEEN ? AND ? ORDER BY checkin_date",
                userId, start, end);

        List<Map<String, Object>> calendar = new ArrayList<>();
        for (Map<String, Object> record : records) {
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("date", record.get("checkin_date").toString());
            day.put("streak", record.get("streak_days"));
            calendar.add(day);
        }
        return calendar;
    }
}
