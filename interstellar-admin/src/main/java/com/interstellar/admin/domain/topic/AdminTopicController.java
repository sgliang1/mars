package com.interstellar.admin.domain.topic;

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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/topics")
@Tag(name = "话题管理", description = "管理员对话题的增删改查")
public class AdminTopicController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping
    @Operation(summary = "话题列表", description = "分页、搜索、筛选")
    public Result<AdminPageResult> list(
            @Parameter(description = "页码") @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(value = "size", defaultValue = "20") int size,
            @Parameter(description = "关键词(标题/slug)") @RequestParam(value = "keyword", required = false) String keyword,
            @Parameter(description = "状态(1上架/0下架)") @RequestParam(value = "status", required = false) Integer status,
            @Parameter(description = "是否高亮") @RequestParam(value = "highlight", required = false) Integer highlight,
            @Parameter(description = "排序字段") @RequestParam(value = "orderBy", defaultValue = "sort_order") String orderBy,
            @Parameter(description = "排序方向") @RequestParam(value = "orderDir", defaultValue = "DESC") String orderDir) {

        AdminQueryBuilder builder = AdminQueryBuilder.from("topic t",
                "t.*, (SELECT COUNT(*) FROM post_topic pt WHERE pt.topic_id = t.id) AS post_count");

        // 筛选条件
        if (keyword != null && !keyword.isBlank()) {
            builder.where("(t.title LIKE ? OR t.slug LIKE ? OR t.keywords LIKE ?)",
                    "%" + keyword + "%", "%" + keyword + "%", "%" + keyword + "%");
        }
        builder.eq("t.status", status);
        builder.eq("t.highlight", highlight);

        // 排序白名单
        String safeOrderBy = switch (orderBy) {
            case "post_count" -> "post_count";
            case "id" -> "t.id";
            case "title" -> "t.title";
            default -> "t.sort_order";
        };
        builder.orderBy(safeOrderBy + " " + ("ASC".equalsIgnoreCase(orderDir) ? "ASC" : "DESC") + ", t.id DESC");

        AdminQueryDTO query = new AdminQueryDTO();
        query.setPage(page);
        query.setSize(size);

        Long total = jdbcTemplate.queryForObject(builder.buildCount(), Long.class, builder.buildParams());
        List<Map<String, Object>> records = jdbcTemplate.queryForList(builder.buildSelect(query), builder.buildParams());

        return Result.success(new AdminPageResult(
                total != null ? total : 0, records, page, size));
    }

    @PostMapping
    @Operation(summary = "创建话题")
    public Result<Void> create(@RequestBody Map<String, Object> body) {
        String slug = (String) body.get("slug");
        String title = (String) body.get("title");
        if (slug == null || title == null) {
            return Result.fail("slug 和 title 不能为空");
        }

        Integer exist = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM topic WHERE slug = ?", Integer.class, slug);
        if (exist != null && exist > 0) {
            return Result.fail("slug 已存在");
        }

        jdbcTemplate.update(
                "INSERT INTO topic (slug, title, summary, keywords, highlight, icon, sort_order, status) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, 1)",
                slug, title,
                body.get("summary"), body.get("keywords"),
                body.get("highlight"), body.get("icon"),
                body.get("sortOrder") != null ? body.get("sortOrder") : 0);

        return Result.successMessage("话题创建成功");
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑话题")
    public Result<Void> update(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        jdbcTemplate.update(
                "UPDATE topic SET title = ?, summary = ?, keywords = ?, highlight = ?, " +
                        "icon = ?, sort_order = ? WHERE id = ?",
                body.get("title"), body.get("summary"), body.get("keywords"),
                body.get("highlight"), body.get("icon"),
                body.get("sortOrder") != null ? body.get("sortOrder") : 0, id);
        return Result.successMessage("话题更新成功");
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "话题上下架")
    public Result<Void> updateStatus(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        Integer status = (Integer) body.get("status");
        if (status == null) {
            return Result.fail("状态不能为空");
        }
        jdbcTemplate.update("UPDATE topic SET status = ? WHERE id = ?", status, id);
        return Result.successMessage("状态更新成功");
    }
}