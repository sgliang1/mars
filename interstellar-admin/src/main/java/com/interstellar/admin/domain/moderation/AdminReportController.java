package com.interstellar.admin.domain.moderation;

import com.interstellar.admin.common.AdminPageResult;
import com.interstellar.admin.common.AdminQueryBuilder;
import com.interstellar.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import com.interstellar.admin.common.AdminQueryDTO;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/reports")
@Tag(name = "举报管理", description = "管理员处理用户举报")
public class AdminReportController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AdminReportService adminReportService;

    @GetMapping
    @Operation(summary = "举报列表", description = "分页、筛选")
    public Result<AdminPageResult> list(
            @Parameter(description = "页码") @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(value = "size", defaultValue = "20") int size,
            @Parameter(description = "状态(0待处理/1已处理/2已忽略)") @RequestParam(value = "status", required = false) Integer status,
            @Parameter(description = "目标类型(post/comment/user)") @RequestParam(value = "targetType", required = false) String targetType,
            @Parameter(description = "举报原因分类") @RequestParam(value = "reason", required = false) String reason,
            @Parameter(description = "举报人ID") @RequestParam(value = "reporterId", required = false) Long reporterId,
            @Parameter(description = "开始时间") @RequestParam(value = "startTime", required = false) String startTime,
            @Parameter(description = "结束时间") @RequestParam(value = "endTime", required = false) String endTime,
            @Parameter(description = "排序字段") @RequestParam(value = "orderBy", defaultValue = "created_at") String orderBy,
            @Parameter(description = "排序方向") @RequestParam(value = "orderDir", defaultValue = "DESC") String orderDir) {

        AdminQueryBuilder builder = AdminQueryBuilder.from("report r",
                "r.*, ru.username AS reporter_name, hu.username AS handler_name");

        // 关联用户名
        builder.join("LEFT JOIN user ru ON r.reporter_id = ru.id");
        builder.join("LEFT JOIN admin_user hu ON r.handler_id = hu.id");

        // 筛选条件
        builder.eq("r.status", status);
        builder.eq("r.target_type", targetType);
        builder.eq("r.reason", reason);
        builder.eq("r.reporter_id", reporterId);

        // 时间范围
        if (startTime != null && !startTime.isBlank()) {
            builder.where("r.created_at >= ?", java.time.LocalDateTime.parse(startTime.replace(" ", "T")));
        }
        if (endTime != null && !endTime.isBlank()) {
            builder.where("r.created_at < ?", java.time.LocalDateTime.parse(endTime.replace(" ", "T")));
        }

        // 排序白名单
        String safeOrderBy = switch (orderBy) {
            case "status" -> "r.status";
            case "handled_at" -> "r.handled_at";
            default -> "r.created_at";
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

    @PutMapping("/{id}/handle")
    @Operation(summary = "处理举报", description = "确认违规（隐藏/删除帖子+可选封禁）或忽略")
    public Result<Void> handle(
            @RequestHeader(value = "X-User-Id", required = false) String adminId,
            @RequestHeader(value = "X-User-Name", required = false) String adminName,
            @PathVariable("id") Long reportId, @RequestBody Map<String, Object> body) {

        String action = (String) body.get("action");
        String postAction = (String) body.get("postAction");
        Number banDaysNum = (Number) body.get("banDays");
        String reason = (String) body.get("reason");

        Long handlerId = adminId != null ? Long.parseLong(adminId) : null;
        String handlerName = adminName;

        if (action == null) {
            return Result.fail("action 不能为空");
        }

        int banDays = banDaysNum != null ? banDaysNum.intValue() : 0;

        try {
            adminReportService.handleReport(reportId, action, postAction, banDays, reason, handlerId, handlerName);
            return Result.successMessage("处理成功");
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }

    private Long getPostUserId(Long postId) {
        List<Map<String, Object>> posts = jdbcTemplate.queryForList(
                "SELECT user_id FROM post WHERE id = ?", postId);
        return posts.isEmpty() ? null : ((Number) posts.get(0).get("user_id")).longValue();
    }

    private Long getCommentUserId(Long commentId) {
        List<Map<String, Object>> comments = jdbcTemplate.queryForList(
                "SELECT user_id FROM comment WHERE id = ?", commentId);
        return comments.isEmpty() ? null : ((Number) comments.get(0).get("user_id")).longValue();
    }

    @GetMapping("/stats")
    @Operation(summary = "举报统计")
    public Result<Map<String, Object>> stats() {
        Long pending = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM report WHERE status = 0", Long.class);
        Long processed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM report WHERE status = 1", Long.class);
        Long ignored = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM report WHERE status = 2", Long.class);

        Map<String, Object> data = new java.util.HashMap<>();
        data.put("pending", pending != null ? pending : 0);
        data.put("processed", processed != null ? processed : 0);
        data.put("ignored", ignored != null ? ignored : 0);
        return Result.success(data);
    }

    @GetMapping("/{id}/context")
    @Operation(summary = "举报上下文", description = "获取举报详情及被举报内容的完整信息")
    public Result<Map<String, Object>> getContext(@PathVariable("id") Long reportId) {
        // 1. 获取举报详情
        List<Map<String, Object>> reports = jdbcTemplate.queryForList(
                "SELECT r.*, ru.username AS reporter_name FROM report r " +
                        "LEFT JOIN user ru ON r.reporter_id = ru.id WHERE r.id = ?", reportId);
        if (reports.isEmpty()) {
            return Result.fail("举报不存在");
        }

        Map<String, Object> report = reports.get(0);
        String targetType = (String) report.get("target_type");
        Long targetId = ((Number) report.get("target_id")).longValue();

        Map<String, Object> data = new HashMap<>();
        data.put("report", report);

        // 2. 根据目标类型获取被举报内容
        switch (targetType) {
            case "post" -> {
                List<Map<String, Object>> posts = jdbcTemplate.queryForList(
                        "SELECT p.*, u.username FROM post p " +
                                "LEFT JOIN user u ON p.user_id = u.id WHERE p.id = ?", targetId);
                if (!posts.isEmpty()) {
                    data.put("targetPost", posts.get(0));
                    data.put("targetUser", getUserBrief(((Number) posts.get(0).get("user_id")).longValue()));
                }
            }
            case "comment" -> {
                List<Map<String, Object>> comments = jdbcTemplate.queryForList(
                        "SELECT c.*, u.username FROM comment c " +
                                "LEFT JOIN user u ON c.user_id = u.id WHERE c.id = ?", targetId);
                if (!comments.isEmpty()) {
                    Map<String, Object> comment = comments.get(0);
                    data.put("targetComment", comment);
                    data.put("targetUser", getUserBrief(((Number) comment.get("user_id")).longValue()));

                    // 获取所属帖子
                    Long postId = ((Number) comment.get("post_id")).longValue();
                    List<Map<String, Object>> parentPosts = jdbcTemplate.queryForList(
                            "SELECT p.id, p.title, p.summary, u.username FROM post p " +
                                    "LEFT JOIN user u ON p.user_id = u.id WHERE p.id = ?", postId);
                    if (!parentPosts.isEmpty()) {
                        data.put("parentPost", parentPosts.get(0));
                    }
                }
            }
            case "user" -> {
                data.put("targetUser", getUserBrief(targetId));
            }
        }

        return Result.success(data);
    }

    private Map<String, Object> getUserBrief(Long userId) {
        List<Map<String, Object>> users = jdbcTemplate.queryForList(
                "SELECT u.id, u.username, u.email, p.nickname, p.avatar_url, p.bio, " +
                        "p.follower_count, p.status, p.created_at " +
                        "FROM user u LEFT JOIN user_profile p ON u.id = p.user_id WHERE u.id = ?", userId);
        return users.isEmpty() ? new HashMap<>() : users.get(0);
    }
}