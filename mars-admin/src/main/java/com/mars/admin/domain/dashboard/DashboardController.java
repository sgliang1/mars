package com.mars.admin.domain.dashboard;

import com.mars.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/dashboard")
@Tag(name = "数据看板", description = "平台数据统计")
public class DashboardController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/overview")
    @Operation(summary = "概览数据", description = "总用户、总帖子、今日新增")
    public Result<Map<String, Object>> overview() {
        Map<String, Object> data = new HashMap<>();

        Long totalUsers = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user", Long.class);
        Long totalPosts = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post", Long.class);

        // 修复：使用范围查询替代 DATE() 函数，确保走索引
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

        return Result.success(data);
    }

    @GetMapping("/trends")
    @Operation(summary = "趋势数据", description = "最近 30 天用户与内容增长")
    public Result<Map<String, Object>> trends() {
        Map<String, Object> data = new HashMap<>();

        // 修复：使用范围查询
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

        return Result.success(data);
    }

    @GetMapping("/active")
    @Operation(summary = "活跃数据", description = "DAU、发帖量、评论量、点赞量")
    public Result<Map<String, Object>> active() {
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

        return Result.success(data);
    }

    @GetMapping("/moderation")
    @Operation(summary = "审核指标", description = "待审核、已审核、通过率、举报趋势")
    public Result<Map<String, Object>> moderation() {
        Map<String, Object> data = new HashMap<>();

        // 待审核帖子数量
        Long pendingReviews = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM post WHERE audit_status = 0 AND deleted_at IS NULL", Long.class);

        // 今日已审核数量
        Long todayReviewed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM post WHERE reviewed_at >= CURDATE() AND reviewed_at < DATE_ADD(CURDATE(), INTERVAL 1 DAY)",
                Long.class);

        // 今日审核通过数量
        Long todayApproved = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM post WHERE reviewed_at >= CURDATE() AND reviewed_at < DATE_ADD(CURDATE(), INTERVAL 1 DAY) AND audit_status = 2",
                Long.class);

        // 待处理举报数量
        Long pendingReports = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM report WHERE status = 0", Long.class);

        // 今日新增举报数
        Long todayNewReports = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM report WHERE created_at >= CURDATE() AND created_at < DATE_ADD(CURDATE(), INTERVAL 1 DAY)",
                Long.class);

        // 近7天举报趋势
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

        return Result.success(data);
    }

    @GetMapping("/alerts")
    @Operation(summary = "风险告警", description = "审核积压、多举报帖子、多举报用户")
    public Result<Map<String, Object>> alerts() {
        Map<String, Object> data = new HashMap<>();

        // 审核积压告警：待审核帖子超过阈值
        Long pendingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM post WHERE audit_status = 0 AND deleted_at IS NULL", Long.class);
        boolean backlogAlert = pendingCount != null && pendingCount > 50;
        data.put("backlogAlert", backlogAlert);
        data.put("backlogCount", pendingCount != null ? pendingCount : 0);

        // 被多人举报的帖子（最近7天内被举报2次以上的帖子）
        List<Map<String, Object>> hotReportedPosts = jdbcTemplate.queryForList(
                "SELECT r.target_id, COUNT(*) AS report_count, p.title " +
                        "FROM report r LEFT JOIN post p ON r.target_id = p.id " +
                        "WHERE r.target_type = 'post' AND r.status = 0 " +
                        "AND r.created_at >= DATE_SUB(CURDATE(), INTERVAL 7 DAY) " +
                        "GROUP BY r.target_id, p.title HAVING COUNT(*) >= 2 " +
                        "ORDER BY report_count DESC LIMIT 10");
        data.put("hotReportedPosts", hotReportedPosts);

        // 被多人举报的用户（最近7天内被举报2次以上的用户）
        List<Map<String, Object>> hotReportedUsers = jdbcTemplate.queryForList(
                "SELECT r.target_id AS user_id, u.username, COUNT(*) AS report_count " +
                        "FROM report r LEFT JOIN user u ON r.target_id = u.id " +
                        "WHERE r.target_type = 'user' AND r.status = 0 " +
                        "AND r.created_at >= DATE_SUB(CURDATE(), INTERVAL 7 DAY) " +
                        "GROUP BY r.target_id, u.username HAVING COUNT(*) >= 2 " +
                        "ORDER BY report_count DESC LIMIT 10");
        data.put("hotReportedUsers", hotReportedUsers);

        return Result.success(data);
    }
}