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
@RequestMapping("/admin/badges")
public class AdminBadgeController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping
    public AdminPageResult list(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "category", required = false) String category) {

        AdminQueryBuilder qb = AdminQueryBuilder.from("badge_definition", "*")
                .eq("status", status)
                .eq("category", category)
                .orderBy("created_at DESC");

        AdminQueryDTO query = new AdminQueryDTO();
        query.setPage(page);
        query.setSize(size);

        Long total = jdbcTemplate.queryForObject(qb.buildCount(), Long.class, qb.buildParams());
        List<Map<String, Object>> records = jdbcTemplate.queryForList(qb.buildSelect(query), qb.buildParams());
        return new AdminPageResult(total != null ? total : 0, records, page, size);
    }

    @PostMapping
    @AdminAudit(action = "创建勋章")
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        if (name == null || name.isBlank()) {
            return Map.of("code", 400, "msg", "名称不能为空");
        }

        jdbcTemplate.update(
                "INSERT INTO badge_definition (name, description, icon_url, rarity, category, max_supply, conditions, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                name,
                body.getOrDefault("description", ""),
                body.getOrDefault("iconUrl", ""),
                body.getOrDefault("rarity", "common"),
                body.getOrDefault("category", "achievement"),
                body.get("maxSupply"),
                body.get("conditions") != null ? body.get("conditions").toString() : null,
                1);

        return Map.of("code", 200, "msg", "创建成功");
    }

    @PutMapping("/{id}")
    @AdminAudit(action = "更新勋章")
    public Map<String, Object> update(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        StringBuilder sql = new StringBuilder("UPDATE badge_definition SET ");
        java.util.List<Object> params = new java.util.ArrayList<>();

        if (body.containsKey("name")) { sql.append("name = ?, "); params.add(body.get("name")); }
        if (body.containsKey("description")) { sql.append("description = ?, "); params.add(body.get("description")); }
        if (body.containsKey("iconUrl")) { sql.append("icon_url = ?, "); params.add(body.get("iconUrl")); }
        if (body.containsKey("rarity")) { sql.append("rarity = ?, "); params.add(body.get("rarity")); }
        if (body.containsKey("category")) { sql.append("category = ?, "); params.add(body.get("category")); }
        if (body.containsKey("maxSupply")) { sql.append("max_supply = ?, "); params.add(body.get("maxSupply")); }
        if (body.containsKey("conditions")) { sql.append("conditions = ?, "); params.add(body.get("conditions") != null ? body.get("conditions").toString() : null); }

        if (params.isEmpty()) return Map.of("code", 400, "msg", "无更新内容");

        String setClause = sql.substring(0, sql.length() - 2);
        params.add(id);
        jdbcTemplate.update("UPDATE badge_definition SET " + setClause + " WHERE id = ?", params.toArray());

        return Map.of("code", 200, "msg", "更新成功");
    }

    @PutMapping("/{id}/status")
    @AdminAudit(action = "切换勋章状态")
    public Map<String, Object> toggleStatus(@PathVariable("id") Long id) {
        List<Map<String, Object>> items = jdbcTemplate.queryForList("SELECT status FROM badge_definition WHERE id = ?", id);
        if (items.isEmpty()) return Map.of("code", 400, "msg", "勋章不存在");

        Integer current = (Integer) items.get(0).get("status");
        int newStatus = (current != null && current == 1) ? -1 : 1;
        jdbcTemplate.update("UPDATE badge_definition SET status = ? WHERE id = ?", newStatus, id);

        return Map.of("code", 200, "msg", newStatus == 1 ? "已上线" : "已下架");
    }

    @PostMapping("/{id}/award")
    @AdminAudit(action = "手动颁发勋章")
    public Map<String, Object> award(@PathVariable("id") Long badgeId, @RequestBody Map<String, Object> body) {
        Long userId = body.get("userId") instanceof Number
                ? ((Number) body.get("userId")).longValue()
                : Long.parseLong(body.get("userId").toString());

        // 检查是否已拥有
        Long existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_badge WHERE user_id = ? AND badge_id = ?",
                Long.class, userId, badgeId);
        if (existing != null && existing > 0) {
            return Map.of("code", 400, "msg", "该用户已拥有此勋章");
        }

        // 检查供应量
        Map<String, Object> badge = jdbcTemplate.queryForMap(
                "SELECT max_supply, current_supply FROM badge_definition WHERE id = ?", badgeId);
        Integer maxSupply = (Integer) badge.get("max_supply");
        Integer currentSupply = (Integer) badge.get("current_supply");
        if (maxSupply != null && currentSupply != null && currentSupply >= maxSupply) {
            return Map.of("code", 400, "msg", "已达最大供应量");
        }

        jdbcTemplate.update(
                "INSERT INTO user_badge (user_id, badge_id, source) VALUES (?, ?, 'admin')",
                userId, badgeId);
        jdbcTemplate.update(
                "UPDATE badge_definition SET current_supply = current_supply + 1 WHERE id = ?", badgeId);

        return Map.of("code", 200, "msg", "颁发成功");
    }

    @GetMapping("/{id}/recipients")
    public AdminPageResult recipients(
            @PathVariable("id") Long badgeId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {

        long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_badge WHERE badge_id = ?", Long.class, badgeId);

        int offset = (page - 1) * size;
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
                "SELECT ub.user_id, ub.awarded_at, ub.source, u.username " +
                "FROM user_badge ub LEFT JOIN user u ON ub.user_id = u.id " +
                "WHERE ub.badge_id = ? ORDER BY ub.awarded_at DESC LIMIT ? OFFSET ?",
                badgeId, size, offset);

        return new AdminPageResult(total, records, page, size);
    }
}
