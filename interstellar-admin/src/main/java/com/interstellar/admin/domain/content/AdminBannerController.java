package com.interstellar.admin.domain.content;

import com.interstellar.admin.common.AdminAudit;
import com.interstellar.admin.common.AdminPageResult;
import com.interstellar.admin.common.AdminQueryDTO;
import com.interstellar.admin.common.AdminQueryBuilder;
import com.interstellar.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/banners")
@Tag(name = "Banner管理", description = "运营位Banner的增删改查")
public class AdminBannerController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping
    @Operation(summary = "Banner列表")
    public Result<AdminPageResult> list(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "position", required = false) String position,
            @RequestParam(value = "status", required = false) Integer status) {

        AdminQueryDTO query = new AdminQueryDTO();
        query.setPage(page);
        query.setSize(size);

        String columns = "id, title, image_url, link_type, link_value, position, sort_order, status, start_time, end_time, created_at";
        AdminQueryBuilder builder = AdminQueryBuilder.from("banner", columns);

        if (position != null && !position.isBlank()) {
            builder.where("position = ?", position);
        }
        builder.eq("status", status);
        builder.orderBy("sort_order DESC, created_at DESC");

        Long total = jdbcTemplate.queryForObject(builder.buildCount(), Long.class, builder.buildParams());
        List<Map<String, Object>> records = jdbcTemplate.queryForList(builder.buildSelect(query), builder.buildParams());

        return Result.success(new AdminPageResult(total != null ? total : 0, records, page, size));
    }

    @PostMapping
    @AdminAudit(action = "create_banner", targetType = "banner", description = "创建Banner")
    @Operation(summary = "创建Banner")
    public Result<String> create(@RequestBody Map<String, Object> body) {
        String title = (String) body.get("title");
        String imageUrl = (String) body.get("imageUrl");
        String linkType = (String) body.getOrDefault("linkType", "url");
        String linkValue = (String) body.getOrDefault("linkValue", "");
        String position = (String) body.getOrDefault("position", "home_top");
        Integer sortOrder = (Integer) body.getOrDefault("sortOrder", 0);
        Integer status = (Integer) body.getOrDefault("status", 1);
        String startTime = (String) body.get("startTime");
        String endTime = (String) body.get("endTime");

        if (title == null || title.isBlank()) return Result.fail("标题不能为空");
        if (imageUrl == null || imageUrl.isBlank()) return Result.fail("图片URL不能为空");

        jdbcTemplate.update(
                "INSERT INTO banner (title, image_url, link_type, link_value, position, sort_order, status, start_time, end_time) VALUES (?,?,?,?,?,?,?,?,?)",
                title, imageUrl, linkType, linkValue, position, sortOrder, status,
                startTime != null ? java.time.LocalDateTime.parse(startTime.replace(" ", "T")) : null,
                endTime != null ? java.time.LocalDateTime.parse(endTime.replace(" ", "T")) : null);

        return Result.successMessage("创建成功");
    }

    @PutMapping("/{id}")
    @AdminAudit(action = "update_banner", targetType = "banner", description = "更新Banner")
    @Operation(summary = "更新Banner")
    public Result<String> update(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        StringBuilder sql = new StringBuilder("UPDATE banner SET ");
        java.util.List<Object> params = new java.util.ArrayList<>();

        if (body.containsKey("title")) { sql.append("title = ?, "); params.add(body.get("title")); }
        if (body.containsKey("imageUrl")) { sql.append("image_url = ?, "); params.add(body.get("imageUrl")); }
        if (body.containsKey("linkType")) { sql.append("link_type = ?, "); params.add(body.get("linkType")); }
        if (body.containsKey("linkValue")) { sql.append("link_value = ?, "); params.add(body.get("linkValue")); }
        if (body.containsKey("position")) { sql.append("position = ?, "); params.add(body.get("position")); }
        if (body.containsKey("sortOrder")) { sql.append("sort_order = ?, "); params.add(body.get("sortOrder")); }
        if (body.containsKey("status")) { sql.append("status = ?, "); params.add(body.get("status")); }
        if (body.containsKey("startTime")) {
            String v = (String) body.get("startTime");
            sql.append("start_time = ?, ");
            params.add(v != null ? java.time.LocalDateTime.parse(v.replace(" ", "T")) : null);
        }
        if (body.containsKey("endTime")) {
            String v = (String) body.get("endTime");
            sql.append("end_time = ?, ");
            params.add(v != null ? java.time.LocalDateTime.parse(v.replace(" ", "T")) : null);
        }

        if (params.isEmpty()) return Result.fail("无更新内容");

        sql.append("updated_at = NOW() WHERE id = ?");
        params.add(id);

        jdbcTemplate.update(sql.toString(), params.toArray());
        return Result.successMessage("更新成功");
    }

    @DeleteMapping("/{id}")
    @AdminAudit(action = "delete_banner", targetType = "banner", description = "删除Banner")
    @Operation(summary = "删除Banner")
    public Result<String> delete(@PathVariable("id") Long id) {
        jdbcTemplate.update("DELETE FROM banner WHERE id = ?", id);
        return Result.successMessage("删除成功");
    }

    @PutMapping("/{id}/status")
    @AdminAudit(action = "toggle_banner", targetType = "banner", description = "Banner上下架")
    @Operation(summary = "上下架Banner")
    public Result<String> toggleStatus(@PathVariable("id") Long id) {
        List<Map<String, Object>> banners = jdbcTemplate.queryForList("SELECT status FROM banner WHERE id = ?", id);
        if (banners.isEmpty()) return Result.fail("Banner不存在");

        Integer current = (Integer) banners.get(0).get("status");
        int newStatus = (current != null && current == 1) ? 0 : 1;
        jdbcTemplate.update("UPDATE banner SET status = ? WHERE id = ?", newStatus, id);

        return Result.successMessage(newStatus == 1 ? "已上架" : "已下架");
    }
}