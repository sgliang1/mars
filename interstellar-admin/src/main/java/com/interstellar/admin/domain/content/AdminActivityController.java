package com.interstellar.admin.domain.content;

import com.interstellar.admin.common.AdminAudit;
import com.interstellar.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/activities")
@Tag(name = "活动管理", description = "管理员活动CRUD")
public class AdminActivityController {

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
    @AdminAudit(action = "创建活动")
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
    @AdminAudit(action = "更新活动")
    public Result<String> update(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        StringBuilder sql = new StringBuilder("UPDATE topic_activity SET ");
        java.util.List<Object> params = new java.util.ArrayList<>();

        if (body.containsKey("title")) { sql.append("title = ?, "); params.add(body.get("title")); }
        if (body.containsKey("summary")) { sql.append("summary = ?, "); params.add(body.get("summary")); }
        if (body.containsKey("rules")) { sql.append("rules = ?, "); params.add(body.get("rules")); }
        if (body.containsKey("coverImage")) { sql.append("cover_image = ?, "); params.add(body.get("coverImage")); }
        if (body.containsKey("status")) { sql.append("status = ?, "); params.add(body.get("status")); }

        if (params.isEmpty()) return Result.fail("无更新内容");

        String setClause = sql.substring(0, sql.length() - 2);
        params.add(id);
        jdbcTemplate.update("UPDATE topic_activity SET " + setClause + " WHERE id = ?", params.toArray());

        return Result.successMessage("更新成功");
    }

    @PutMapping("/{id}/status")
    @AdminAudit(action = "切换活动状态")
    public Result<String> toggleStatus(@PathVariable("id") Long id) {
        List<Map<String, Object>> activities = jdbcTemplate.queryForList("SELECT status FROM topic_activity WHERE id = ?", id);
        if (activities.isEmpty()) return Result.fail("活动不存在");

        Integer current = (Integer) activities.get(0).get("status");
        int newStatus = (current != null && current == 1) ? -1 : 1;
        jdbcTemplate.update("UPDATE topic_activity SET status = ? WHERE id = ?", newStatus, id);

        return Result.successMessage(newStatus == 1 ? "已上线" : "已下线");
    }
}
