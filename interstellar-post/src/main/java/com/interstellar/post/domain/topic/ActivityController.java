package com.interstellar.post.domain.topic;

import com.interstellar.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/topic/activities")
@Tag(name = "活动", description = "话题活动展示与参与")
public class ActivityController {

    @Autowired private JdbcTemplate jdbcTemplate;

    @GetMapping
    @Operation(summary = "进行中的活动列表")
    public Result<List<Map<String, Object>>> list(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {

        int offset = (page - 1) * size;
        List<Map<String, Object>> activities = jdbcTemplate.queryForList(
                "SELECT a.*, t.title AS topic_title, t.slug AS topic_slug " +
                "FROM topic_activity a LEFT JOIN topic t ON a.topic_id = t.id " +
                "WHERE a.status = 1 AND a.start_time <= NOW() AND a.end_time >= NOW() " +
                "ORDER BY a.participant_count DESC, a.created_at DESC " +
                "LIMIT ? OFFSET ?", size, offset);
        return Result.success(activities);
    }

    @GetMapping("/{id}")
    @Operation(summary = "活动详情")
    public Result<Map<String, Object>> detail(@PathVariable("id") Long activityId) {
        List<Map<String, Object>> activities = jdbcTemplate.queryForList(
                "SELECT a.*, t.title AS topic_title, t.slug AS topic_slug " +
                "FROM topic_activity a LEFT JOIN topic t ON a.topic_id = t.id " +
                "WHERE a.id = ?", activityId);
        if (activities.isEmpty()) return Result.fail("活动不存在");

        Map<String, Object> activity = activities.get(0);

        // 获取参与帖子列表
        List<Map<String, Object>> posts = jdbcTemplate.queryForList(
                "SELECT p.id, p.user_id, p.username, p.title, p.summary, p.like_count, p.comment_count, p.create_time " +
                "FROM activity_participant ap JOIN post p ON ap.post_id = p.id " +
                "WHERE ap.activity_id = ? AND p.display_status = 1 " +
                "ORDER BY p.create_time DESC LIMIT 50", activityId);
        activity.put("posts", posts);

        return Result.success(activity);
    }

    @PostMapping("/{id}/join")
    @Operation(summary = "参与活动（发帖关联）")
    public Result<String> join(
            @PathVariable("id") Long activityId,
            @RequestBody Map<String, Long> body,
            @RequestHeader("X-User-Id") String userIdStr) {

        Long postId = body.get("postId");
        Long userId = Long.parseLong(userIdStr);
        if (postId == null) return Result.fail("帖子ID不能为空");

        // 校验活动存在且进行中
        List<Map<String, Object>> activities = jdbcTemplate.queryForList(
                "SELECT id, topic_id, status, end_time FROM topic_activity WHERE id = ?", activityId);
        if (activities.isEmpty()) return Result.fail("活动不存在");

        Map<String, Object> activity = activities.get(0);
        Integer status = (Integer) activity.get("status");
        if (status == null || status != 1) return Result.fail("活动已结束");

        // 关联帖子到话题
        Long topicId = ((Number) activity.get("topic_id")).longValue();
        try {
            jdbcTemplate.update("INSERT IGNORE INTO post_topic (post_id, topic_id) VALUES (?, ?)", postId, topicId);
        } catch (Exception ignored) {}

        // 记录参与
        try {
            jdbcTemplate.update("INSERT IGNORE INTO activity_participant (activity_id, post_id, user_id) VALUES (?, ?, ?)",
                    activityId, postId, userId);
            jdbcTemplate.update("UPDATE topic_activity SET participant_count = participant_count + 1 WHERE id = ?", activityId);
        } catch (Exception e) {
            return Result.fail("你已参与过该活动");
        }

        return Result.successMessage("参与成功");
    }

    @GetMapping("/{id}/ranking")
    @Operation(summary = "活动排行榜", description = "按点赞数或参与时间排序")
    public Result<List<Map<String, Object>>> ranking(
            @PathVariable("id") Long activityId,
            @RequestParam(value = "sortBy", defaultValue = "likes") String sortBy,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {

        String orderClause = "likes".equals(sortBy)
                ? "p.like_count DESC, ap.created_at ASC"
                : "ap.created_at ASC";

        List<Map<String, Object>> ranking = jdbcTemplate.queryForList(
                "SELECT ap.user_id, p.username, ap.post_id, p.title, p.like_count, p.comment_count, ap.created_at AS joined_at " +
                "FROM activity_participant ap " +
                "JOIN post p ON ap.post_id = p.id " +
                "WHERE ap.activity_id = ? AND p.display_status = 1 AND p.deleted_at IS NULL " +
                "ORDER BY " + orderClause + " LIMIT ?",
                activityId, limit);

        // 添加排名序号
        for (int i = 0; i < ranking.size(); i++) {
            ranking.get(i).put("rank", i + 1);
        }

        return Result.success(ranking);
    }
}