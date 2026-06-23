package com.mars.admin.domain.moderation;

import com.mars.admin.common.AdminPageResult;
import com.mars.admin.common.AdminQueryBuilder;
import com.mars.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import com.mars.admin.common.AdminQueryDTO;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/reports")
@Tag(name = "举报管理", description = "管理员处理用户举报")
public class AdminReportController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    @Operation(summary = "处理举报", description = "确认违规时触发举报击穿，将帖子移入复审池")
    public Result<Void> handle(
            @RequestHeader(value = "X-User-Id", required = false) String adminId,
            @RequestHeader(value = "X-User-Name", required = false) String adminName,
            @PathVariable("id") Long reportId, @RequestBody Map<String, Object> body) {
        Integer status = (Integer) body.get("status"); // 1已处理/2已忽略
        String result = (String) body.get("result");

        Long handlerId = adminId != null ? Long.parseLong(adminId) : null;
        String handlerName = adminName;

        if (status == null || (status != 1 && status != 2)) {
            return Result.fail("状态无效");
        }

        // 获取举报信息
        List<Map<String, Object>> reports = jdbcTemplate.queryForList(
                "SELECT * FROM report WHERE id = ?", reportId);
        if (reports.isEmpty()) {
            return Result.fail("举报不存在");
        }

        Map<String, Object> report = reports.get(0);
        String targetType = (String) report.get("target_type");
        Long targetId = ((Number) report.get("target_id")).longValue();

        // 更新举报状态
        int rows = jdbcTemplate.update(
                "UPDATE report SET status = ?, handler_id = ?, handle_result = ?, handled_at = NOW() WHERE id = ?",
                status, handlerId, result, reportId);

        if (rows == 0) {
            return Result.fail("举报不存在");
        }

        // 举报击穿逻辑：确认违规时，强制将帖子移入复审池
        if (status == 1 && "post".equals(targetType)) {
            // 1. 获取帖子当前审核状态
            List<Map<String, Object>> posts = jdbcTemplate.queryForList(
                    "SELECT audit_status FROM post WHERE id = ?", targetId);

            if (!posts.isEmpty()) {
                Integer currentAuditStatus = (Integer) posts.get(0).get("audit_status");

                // 2. 举报击穿：无视原审核状态，强制进入复审
                jdbcTemplate.update(
                        "UPDATE post SET prev_audit_status = ?, audit_status = 4, last_auditor_id = ?, reviewed_by = ?, reviewed_at = NOW() WHERE id = ?",
                        currentAuditStatus, handlerId, handlerId, targetId);
            }
        }

        // 记录操作日志
        jdbcTemplate.update(
                "INSERT INTO admin_audit_log (admin_id, admin_username, action, target_type, target_id, detail, created_at) " +
                        "VALUES (?, ?, 'resolve_report', 'report', ?, ?, NOW())",
                handlerId, handlerName, reportId, result);

        return Result.successMessage("处理成功");
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
}