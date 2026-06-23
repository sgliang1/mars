package com.mars.post.domain.topic;

import com.mars.common.Result;
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
}

@RestController
@RequestMapping("/admin/activities")
@Tag(name = "活动管理", description = "管理员活动CRUD")
class AdminActivityController {

    @Autowired private JdbcTemplate jdbcTemplate;

    @GetMapping
    @Operation(summary = "活动列表", description = "分页查询，关联话题信息")
    public Result<Map<String, Object>> list(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "status", required = false) Integer status) {
        int offset = (page - 1) * size;
        StringBuilder where = new StringBuilder("1=1");
        java.util.List<Object> params = new java.util.ArrayList<>();
        if (status != null) { where.append(" AND a.status = ?"); params.add(status); }

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM topic_activity a WHERE " + where, Long.class, params.toArray());

        params.add(size); params.add(offset);
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
                "SELECT a.*, t.title AS topic_title, t.slug AS topic_slug " +
                "FROM topic_activity a LEFT JOIN topic t ON a.topic_id = t.id " +
                "WHERE " + where + " ORDER BY a.created_at DESC LIMIT ? OFFSET ?", params.toArray());

        Map<String, Object> data = new java.util.HashMap<>();
        data.put("records", records);
        data.put("total", total != null ? total : 0);
        data.put("page", page);
        data.put("size", size);
        return Result.success(data);
    }

    @PostMapping
    public Result<String> create(@RequestBody Map<String, Object> body) {
        Long topicId = ((Number) body.get("topicId")).longValue();
        String title = (String) body.get("title");
        String summary = (String) body.getOrDefault("summary", "");
        String rules = (String) body.getOrDefault("rules", "");
        String coverImage = (String) body.getOrDefault("coverImage", "");
        String startTime = (String) body.get("startTime");
        String endTime = (String) body.get("endTime");
        String activityType = (String) body.getOrDefault("activityType", "challenge");

        if (title == null || title.isBlank()) return Result.fail("标题不能为空");
        if (startTime == null || endTime == null) return Result.fail("时间不能为空");

        jdbcTemplate.update(
                "INSERT INTO topic_activity (topic_id, activity_type, title, summary, rules, cover_image, start_time, end_time) VALUES (?,?,?,?,?,?,?,?)",
                topicId, activityType, title, summary, rules, coverImage,
                java.time.LocalDateTime.parse(startTime.replace(" ", "T")),
                java.time.LocalDateTime.parse(endTime.replace(" ", "T")));

        return Result.successMessage("创建成功");
    }

    @PutMapping("/{id}")
    public Result<String> update(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        StringBuilder sql = new StringBuilder("UPDATE topic_activity SET ");
        java.util.List<Object> params = new java.util.ArrayList<>();

        if (body.containsKey("title")) { sql.append("title = ?, "); params.add(body.get("title")); }
        if (body.containsKey("summary")) { sql.append("summary = ?, "); params.add(body.get("summary")); }
        if (body.containsKey("rules")) { sql.append("rules = ?, "); params.add(body.get("rules")); }
        if (body.containsKey("coverImage")) { sql.append("cover_image = ?, "); params.add(body.get("coverImage")); }
        if (body.containsKey("status")) { sql.append("status = ?, "); params.add(body.get("status")); }

        if (params.isEmpty()) return Result.fail("无更新内容");

        sql.append("id = id WHERE id = ?"); // 占位，避免末尾逗号
        params.add(id);

        // 重新构建 SQL 去掉末尾多余逗号
        String finalSql = sql.toString().replace(", id = id", "");
        jdbcTemplate.update(finalSql + " WHERE id = ?", params.toArray());

        return Result.successMessage("更新成功");
    }

    @PutMapping("/{id}/status")
    public Result<String> toggleStatus(@PathVariable("id") Long id) {
        List<Map<String, Object>> activities = jdbcTemplate.queryForList("SELECT status FROM topic_activity WHERE id = ?", id);
        if (activities.isEmpty()) return Result.fail("活动不存在");

        Integer current = (Integer) activities.get(0).get("status");
        int newStatus = (current != null && current == 1) ? -1 : 1;
        jdbcTemplate.update("UPDATE topic_activity SET status = ? WHERE id = ?", newStatus, id);

        return Result.successMessage(newStatus == 1 ? "已上线" : "已下线");
    }
}