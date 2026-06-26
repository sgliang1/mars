package com.interstellar.user.domain.dashboard;

import com.interstellar.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/dashboard")
@Tag(name = "创作者数据看板", description = "用户端创作者数据分析")
class CreatorAnalyticsController {

    @Autowired private JdbcTemplate jdbcTemplate;

    @GetMapping("/{userId}/analytics")
    @Operation(summary = "创作者数据看板", description = "近7/30天阅读量、互动量趋势、单帖TOP10")
    public Result<Map<String, Object>> analytics(
            @PathVariable("userId") Long userId,
            @RequestParam(value = "days", defaultValue = "7") int days) {

        LocalDateTime since = LocalDateTime.now().minusDays(days);
        Map<String, Object> result = new HashMap<>();

        // 1. 阅读量趋势（按天聚合）
        List<Map<String, Object>> viewTrend = jdbcTemplate.queryForList(
                "SELECT DATE(last_viewed_at) AS date, SUM(view_count) AS views " +
                "FROM post_browse_history WHERE user_id IN (SELECT id FROM post WHERE user_id = ?) " +
                "AND last_viewed_at >= ? " +
                "GROUP BY DATE(last_viewed_at) ORDER BY date",
                userId, since);
        result.put("viewTrend", viewTrend);

        // 2. 互动量趋势（点赞/评论/转发分项）
        List<Map<String, Object>> likeTrend = jdbcTemplate.queryForList(
                "SELECT DATE(create_time) AS date, COUNT(*) AS count " +
                "FROM post_like WHERE post_id IN (SELECT id FROM post WHERE user_id = ?) " +
                "AND create_time >= ? GROUP BY DATE(create_time) ORDER BY date",
                userId, since);
        List<Map<String, Object>> commentTrend = jdbcTemplate.queryForList(
                "SELECT DATE(create_time) AS date, COUNT(*) AS count " +
                "FROM comment WHERE post_id IN (SELECT id FROM post WHERE user_id = ?) " +
                "AND create_time >= ? AND deleted_at IS NULL GROUP BY DATE(create_time) ORDER BY date",
                userId, since);
        List<Map<String, Object>> repostTrend = jdbcTemplate.queryForList(
                "SELECT DATE(create_time) AS date, COUNT(*) AS count " +
                "FROM post_repost WHERE original_post_id IN (SELECT id FROM post WHERE user_id = ?) " +
                "AND create_time >= ? GROUP BY DATE(create_time) ORDER BY date",
                userId, since);

        Map<String, Object> interactionTrend = new HashMap<>();
        interactionTrend.put("likes", likeTrend);
        interactionTrend.put("comments", commentTrend);
        interactionTrend.put("reposts", repostTrend);
        result.put("interactionTrend", interactionTrend);

        // 3. 概览数据
        Long totalViews = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(view_count), 0) FROM post_browse_history " +
                "WHERE user_id IN (SELECT id FROM post WHERE user_id = ?)", Long.class, userId);
        Long totalLikes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM post_like " +
                "WHERE post_id IN (SELECT id FROM post WHERE user_id = ?)", Long.class, userId);
        Long totalComments = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM comment " +
                "WHERE post_id IN (SELECT id FROM post WHERE user_id = ?) AND deleted_at IS NULL", Long.class, userId);
        Long followerCount = jdbcTemplate.queryForObject(
                "SELECT follower_count FROM user_profile WHERE user_id = ?", Long.class, userId);

        Map<String, Object> overview = new HashMap<>();
        overview.put("totalViews", totalViews != null ? totalViews : 0);
        overview.put("totalLikes", totalLikes != null ? totalLikes : 0);
        overview.put("totalComments", totalComments != null ? totalComments : 0);
        overview.put("followerCount", followerCount != null ? followerCount : 0);
        result.put("overview", overview);

        // 4. 单帖表现 TOP 10（按阅读量）
        List<Map<String, Object>> topPosts = jdbcTemplate.queryForList(
                "SELECT p.id, p.title, p.summary, p.like_count, p.comment_count, p.share_count, p.create_time, " +
                "COALESCE(SUM(bh.view_count), 0) AS total_views " +
                "FROM post p LEFT JOIN post_browse_history bh ON p.id = bh.post_id " +
                "WHERE p.user_id = ? " +
                "GROUP BY p.id ORDER BY total_views DESC LIMIT 10",
                userId);
        result.put("topPosts", topPosts);

        return Result.success(result);
    }
}