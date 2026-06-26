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
@RequestMapping("/admin/comments")
@Tag(name = "评论管理", description = "管理员对评论的审核与管理")
public class AdminCommentController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping
    @Operation(summary = "评论列表", description = "分页、搜索、多维度筛选，默认排除已删除评论")
    public Result<AdminPageResult> list(
            @Parameter(description = "页码") @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(value = "size", defaultValue = "20") int size,
            @Parameter(description = "帖子ID") @RequestParam(value = "postId", required = false) Long postId,
            @Parameter(description = "评论者ID") @RequestParam(value = "userId", required = false) Long userId,
            @Parameter(description = "评论内容关键词") @RequestParam(value = "keyword", required = false) String keyword,
            @Parameter(description = "帖子标题关键词") @RequestParam(value = "postTitle", required = false) String postTitle,
            @Parameter(description = "是否为回复(有parent_id)") @RequestParam(value = "isReply", required = false) Boolean isReply,
            @Parameter(description = "是否包含已删除评论") @RequestParam(value = "includeDeleted", defaultValue = "false") boolean includeDeleted,
            @Parameter(description = "开始时间") @RequestParam(value = "startTime", required = false) String startTime,
            @Parameter(description = "结束时间") @RequestParam(value = "endTime", required = false) String endTime,
            @Parameter(description = "排序字段") @RequestParam(value = "orderBy", defaultValue = "create_time") String orderBy,
            @Parameter(description = "排序方向") @RequestParam(value = "orderDir", defaultValue = "DESC") String orderDir) {

        AdminQueryBuilder builder = AdminQueryBuilder.from("comment c",
                "c.id, c.post_id, c.user_id, c.username, c.content, c.parent_id, c.create_time, c.deleted_at, c.deleted_by, p.title AS post_title");

        // 关联帖子表获取标题
        builder.join("LEFT JOIN post p ON c.post_id = p.id");

        // 默认排除已删除评论
        if (!includeDeleted) {
            builder.where("c.deleted_at IS NULL");
        }

        // 筛选条件
        builder.eq("c.post_id", postId);
        builder.eq("c.user_id", userId);
        builder.like("c.content", keyword);
        builder.like("p.title", postTitle);

        // 是否为回复
        if (Boolean.TRUE.equals(isReply)) {
            builder.where("c.parent_id IS NOT NULL AND c.parent_id > 0");
        } else if (Boolean.FALSE.equals(isReply)) {
            builder.where("(c.parent_id IS NULL OR c.parent_id = 0)");
        }

        // 时间范围
        if (startTime != null && !startTime.isBlank()) {
            builder.where("c.create_time >= ?", java.time.LocalDateTime.parse(startTime.replace(" ", "T")));
        }
        if (endTime != null && !endTime.isBlank()) {
            builder.where("c.create_time < ?", java.time.LocalDateTime.parse(endTime.replace(" ", "T")));
        }

        // 排序白名单
        String safeOrderBy = switch (orderBy) {
            case "user_id" -> "c.user_id";
            default -> "c.create_time";
        };
        builder.orderBy(safeOrderBy + " " + ("ASC".equalsIgnoreCase(orderDir) ? "ASC" : "DESC"));

        AdminQueryDTO query = new AdminQueryDTO();
        query.setPage(page);
        query.setSize(size);

        Long total = jdbcTemplate.queryForObject(builder.buildCount(), Long.class, builder.buildParams());
        List<Map<String, Object>> records = jdbcTemplate.queryForList(builder.buildSelect(query), builder.buildParams());

        return Result.success(new AdminPageResult(
                total != null ? total : 0, records, page, size));
    }

    @DeleteMapping("/{id}")
    @AdminAudit(action = "delete_comment", targetType = "comment", description = "软删除评论（保留证据）")
    @Operation(summary = "软删除评论", description = "标记为已删除，保留数据作为证据")
    public Result<Void> delete(
            @RequestHeader(value = "X-User-Id", required = false) String adminId,
            @PathVariable("id") Long commentId) {
        Long adminUserId = adminId != null ? Long.parseLong(adminId) : null;
        int rows = jdbcTemplate.update(
                "UPDATE comment SET deleted_at = NOW(), deleted_by = ? WHERE id = ? AND deleted_at IS NULL",
                adminUserId, commentId);
        if (rows == 0) {
            return Result.fail("评论不存在或已被删除");
        }
        return Result.successMessage("评论已删除");
    }

    @DeleteMapping("/{id}/permanent")
    @AdminAudit(action = "permanent_delete_comment", targetType = "comment", description = "彻底删除评论（不可恢复）")
    @Operation(summary = "彻底删除评论", description = "物理删除，用于最终清理证据链")
    public Result<Void> permanentDelete(@PathVariable("id") Long commentId) {
        // 先删子回复
        jdbcTemplate.update("DELETE FROM comment WHERE parent_id = ?", commentId);
        int rows = jdbcTemplate.update("DELETE FROM comment WHERE id = ?", commentId);
        if (rows == 0) {
            return Result.fail("评论不存在");
        }
        return Result.successMessage("评论已彻底删除");
    }
}