package com.interstellar.admin.domain.dashboard;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class DashboardService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public Map<String, Object> getOverview() {
        try {
            Map<String, Object> data = new HashMap<>();
            Long totalUsers = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user", Long.class);
            Long totalPosts = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post", Long.class);
            Long todayUsers = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM user_profile WHERE created_at >= CURDATE() AND created_at < DATE_ADD(CURDATE(), INTERVAL 1 DAY)",
                    Long.class);
            Long todayPosts = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM post WHERE create_time >= CURDATE() AND create_time < DATE_ADD(CURDATE(), INTERVAL 1 DAY)",
                    Long.class);
            data.put("totalUsers", totalUsers != null ? totalUsers : 0);
            data.put("totalPosts", totalPosts != null ? totalPosts : 0);
            data.put("todayNewUsers", todayUsers != null ? todayUsers : 0);
            data.put("todayNewPosts", todayPosts != null ? todayPosts : 0);
            return data;
        } catch (Exception e) {
            log.error("获取概览数据失败", e);
            return Map.of("totalUsers", 0, "totalPosts", 0, "todayNewUsers", 0, "todayNewPosts", 0);
        }
    }

    public Map<String, Object> getTrends() {
        try {
            Map<String, Object> data = new HashMap<>();
            List<Map<String, Object>> userTrends = jdbcTemplate.queryForList(
                    "SELECT DATE(created_at) AS date, COUNT(*) AS count " +
                            "FROM user_profile WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) " +
                            "AND created_at < DATE_ADD(CURDATE(), INTERVAL 1 DAY) " +
                            "GROUP BY DATE(created_at) ORDER BY date");
            List<Map<String, Object>> postTrends = jdbcTemplate.queryForList(
                    "SELECT DATE(create_time) AS date, COUNT(*) AS count " +
                            "FROM post WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) " +
                            "AND create_time < DATE_ADD(CURDATE(), INTERVAL 1 DAY) " +
                            "GROUP BY DATE(create_time) ORDER BY date");
            data.put("userTrends", userTrends);
            data.put("postTrends", postTrends);
            return data;
        } catch (Exception e) {
            log.error("获取趋势数据失败", e);
            return Map.of("userTrends", Collections.emptyList(), "postTrends", Collections.emptyList());
        }
    }

    public Map<String, Object> getActive() {
        try {
            Map<String, Object> data = new HashMap<>();
            Long dau = jdbcTemplate.queryForObject(
                    "SELECT COUNT(DISTINCT user_id) FROM post_browse_history " +
                            "WHERE last_viewed_at >= CURDATE() AND last_viewed_at < DATE_ADD(CURDATE(), INTERVAL 1 DAY)",
                    Long.class);
            Long todayComments = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM comment " +
                            "WHERE create_time >= CURDATE() AND create_time < DATE_ADD(CURDATE(), INTERVAL 1 DAY) AND deleted_at IS NULL",
                    Long.class);
            Long todayLikes = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM post_like " +
                            "WHERE create_time >= CURDATE() AND create_time < DATE_ADD(CURDATE(), INTERVAL 1 DAY)",
                    Long.class);
            data.put("dau", dau != null ? dau : 0);
            data.put("todayComments", todayComments != null ? todayComments : 0);
            data.put("todayLikes", todayLikes != null ? todayLikes : 0);
            return data;
        } catch (Exception e) {
            log.error("获取活跃数据失败", e);
            return Map.of("dau", 0, "todayComments", 0, "todayLikes", 0);
        }
    }

    public Map<String, Object> getModeration() {
        try {
            Map<String, Object> data = new HashMap<>();
            Long pendingReviews = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM post WHERE audit_status = 0 AND deleted_at IS NULL", Long.class);
            Long todayReviewed = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM post WHERE reviewed_at >= CURDATE() AND reviewed_at < DATE_ADD(CURDATE(), INTERVAL 1 DAY)",
                    Long.class);
            Long todayApproved = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM post WHERE reviewed_at >= CURDATE() AND reviewed_at < DATE_ADD(CURDATE(), INTERVAL 1 DAY) AND audit_status = 2",
                    Long.class);
            Long pendingReports = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM report WHERE status = 0", Long.class);
            Long todayNewReports = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM report WHERE created_at >= CURDATE() AND created_at < DATE_ADD(CURDATE(), INTERVAL 1 DAY)",
                    Long.class);
            List<Map<String, Object>> reportTrends = jdbcTemplate.queryForList(
                    "SELECT DATE(created_at) AS date, COUNT(*) AS count " +
                            "FROM report WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 7 DAY) " +
                            "AND created_at < DATE_ADD(CURDATE(), INTERVAL 1 DAY) " +
                            "GROUP BY DATE(created_at) ORDER BY date");
            data.put("pendingReviews", pendingReviews != null ? pendingReviews : 0);
            data.put("todayReviewed", todayReviewed != null ? todayReviewed : 0);
            data.put("todayApproved", todayApproved != null ? todayApproved : 0);
            data.put("pendingReports", pendingReports != null ? pendingReports : 0);
            data.put("todayNewReports", todayNewReports != null ? todayNewReports : 0);
            data.put("reportTrends", reportTrends);
            return data;
        } catch (Exception e) {
            log.error("获取审核指标失败", e);
            return Map.of("pendingReviews", 0, "todayReviewed", 0, "todayApproved", 0,
                    "pendingReports", 0, "todayNewReports", 0, "reportTrends", Collections.emptyList());
        }
    }

    public Map<String, Object> getAlerts() {
        try {
            Map<String, Object> data = new HashMap<>();
            Long pendingCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM post WHERE audit_status = 0 AND deleted_at IS NULL", Long.class);
            data.put("backlogAlert", pendingCount != null && pendingCount > 50);
            data.put("backlogCount", pendingCount != null ? pendingCount : 0);

            List<Map<String, Object>> hotReportedPosts = jdbcTemplate.queryForList(
                    "SELECT r.target_id, COUNT(*) AS report_count, p.title " +
                            "FROM report r LEFT JOIN post p ON r.target_id = p.id " +
                            "WHERE r.target_type = 'post' AND r.status = 0 " +
                            "AND r.created_at >= DATE_SUB(CURDATE(), INTERVAL 7 DAY) " +
                            "GROUP BY r.target_id, p.title HAVING COUNT(*) >= 2 " +
                            "ORDER BY report_count DESC LIMIT 10");
            data.put("hotReportedPosts", hotReportedPosts);

            List<Map<String, Object>> hotReportedUsers = jdbcTemplate.queryForList(
                    "SELECT r.target_id AS user_id, u.username, COUNT(*) AS report_count " +
                            "FROM report r LEFT JOIN user u ON r.target_id = u.id " +
                            "WHERE r.target_type = 'user' AND r.status = 0 " +
                            "AND r.created_at >= DATE_SUB(CURDATE(), INTERVAL 7 DAY) " +
                            "GROUP BY r.target_id, u.username HAVING COUNT(*) >= 2 " +
                            "ORDER BY report_count DESC LIMIT 10");
            data.put("hotReportedUsers", hotReportedUsers);
            return data;
        } catch (Exception e) {
            log.error("获取风险告警失败", e);
            return Map.of("backlogAlert", false, "backlogCount", 0,
                    "hotReportedPosts", Collections.emptyList(), "hotReportedUsers", Collections.emptyList());
        }
    }
}
