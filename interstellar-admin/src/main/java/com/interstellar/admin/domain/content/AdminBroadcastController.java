package com.interstellar.admin.domain.content;

import com.interstellar.admin.common.AdminAudit;
import com.interstellar.admin.common.AdminPageResult;
import com.interstellar.admin.common.AdminQueryBuilder;
import com.interstellar.admin.common.AdminQueryDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/broadcasts")
public class AdminBroadcastController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping
    public AdminPageResult list(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "status", required = false) Integer status) {

        AdminQueryBuilder qb = AdminQueryBuilder.from("broadcast", "*")
                .eq("status", status)
                .orderBy("created_at DESC");

        AdminQueryDTO query = new AdminQueryDTO();
        query.setPage(page);
        query.setSize(size);

        long total = jdbcTemplate.queryForObject(qb.buildCount(), Long.class, qb.buildParams());
        List<Map<String, Object>> records = jdbcTemplate.queryForList(qb.buildSelect(query), qb.buildParams());
        return new AdminPageResult(total, records, page, size);
    }

    @PostMapping
    @AdminAudit(action = "创建广播")
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String title = (String) body.get("title");
        if (title == null || title.isBlank()) {
            return Map.of("code", 400, "msg", "标题不能为空");
        }

        jdbcTemplate.update(
                "INSERT INTO broadcast (title, content, cover_image, link_type, link_value, " +
                "target_scope, target_value, status, publish_time, expire_time, created_by) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                title,
                body.getOrDefault("content", ""),
                body.getOrDefault("coverImage", ""),
                body.getOrDefault("linkType", "none"),
                body.getOrDefault("linkValue", ""),
                body.getOrDefault("targetScope", "all"),
                body.getOrDefault("targetValue", ""),
                body.getOrDefault("status", 0),
                body.get("publishTime"),
                body.get("expireTime"),
                body.get("createdBy"));

        return Map.of("code", 200, "msg", "创建成功");
    }

    @PutMapping("/{id}")
    @AdminAudit(action = "更新广播")
    public Map<String, Object> update(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        StringBuilder sql = new StringBuilder("UPDATE broadcast SET ");
        java.util.List<Object> params = new java.util.ArrayList<>();

        String[] fields = {"title", "content", "coverImage", "linkType", "linkValue",
                "targetScope", "targetValue", "publishTime", "expireTime"};
        String[] columns = {"title", "content", "cover_image", "link_type", "link_value",
                "target_scope", "target_value", "publish_time", "expire_time"};

        for (int i = 0; i < fields.length; i++) {
            if (body.containsKey(fields[i])) {
                sql.append(columns[i]).append(" = ?, ");
                params.add(body.get(fields[i]));
            }
        }

        if (params.isEmpty()) return Map.of("code", 400, "msg", "无更新内容");

        String setClause = sql.substring(0, sql.length() - 2);
        params.add(id);
        jdbcTemplate.update("UPDATE broadcast SET " + setClause + " WHERE id = ?", params.toArray());

        return Map.of("code", 200, "msg", "更新成功");
    }

    @PutMapping("/{id}/status")
    @AdminAudit(action = "切换广播状态")
    public Map<String, Object> toggleStatus(@PathVariable("id") Long id) {
        List<Map<String, Object>> items = jdbcTemplate.queryForList("SELECT status FROM broadcast WHERE id = ?", id);
        if (items.isEmpty()) return Map.of("code", 400, "msg", "广播不存在");

        Integer current = (Integer) items.get(0).get("status");
        int newStatus;
        if (current == null || current == 0) {
            newStatus = 1; // 发布
            jdbcTemplate.update("UPDATE broadcast SET status = ?, publish_time = NOW() WHERE id = ?", newStatus, id);
        } else {
            newStatus = 0; // 下线
            jdbcTemplate.update("UPDATE broadcast SET status = ? WHERE id = ?", newStatus, id);
        }

        return Map.of("code", 200, "msg", newStatus == 1 ? "已发布" : "已下线");
    }

    @DeleteMapping("/{id}")
    @AdminAudit(action = "删除广播")
    public Map<String, Object> delete(@PathVariable("id") Long id) {
        jdbcTemplate.update("DELETE FROM broadcast WHERE id = ?", id);
        return Map.of("code", 200, "msg", "已删除");
    }
}
