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
@RequestMapping("/admin/audit-logs")
@Tag(name = "操作日志", description = "管理员操作日志查询")
public class AdminAuditLogController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping
    @Operation(summary = "操作日志列表", description = "分页、筛选")
    public Result<AdminPageResult> list(
            @Parameter(description = "页码") @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(value = "size", defaultValue = "20") int size,
            @Parameter(description = "管理员ID") @RequestParam(value = "adminId", required = false) Long adminId,
            @Parameter(description = "操作类型") @RequestParam(value = "action", required = false) String action,
            @Parameter(description = "目标类型") @RequestParam(value = "targetType", required = false) String targetType,
            @Parameter(description = "目标ID") @RequestParam(value = "targetId", required = false) Long targetId,
            @Parameter(description = "开始时间") @RequestParam(value = "startTime", required = false) String startTime,
            @Parameter(description = "结束时间") @RequestParam(value = "endTime", required = false) String endTime,
            @Parameter(description = "排序方向") @RequestParam(value = "orderDir", defaultValue = "DESC") String orderDir) {

        AdminQueryBuilder builder = AdminQueryBuilder.from("admin_audit_log l",
                "l.*");

        // 筛选条件
        builder.eq("l.admin_id", adminId);
        builder.eq("l.action", action);
        builder.eq("l.target_type", targetType);
        builder.eq("l.target_id", targetId);

        // 时间范围
        if (startTime != null && !startTime.isBlank()) {
            builder.where("l.created_at >= ?", java.time.LocalDateTime.parse(startTime.replace(" ", "T")));
        }
        if (endTime != null && !endTime.isBlank()) {
            builder.where("l.created_at < ?", java.time.LocalDateTime.parse(endTime.replace(" ", "T")));
        }

        builder.orderBy("l.created_at " + ("ASC".equalsIgnoreCase(orderDir) ? "ASC" : "DESC"));

        AdminQueryDTO query = new AdminQueryDTO();
        query.setPage(page);
        query.setSize(size);

        Long total = jdbcTemplate.queryForObject(builder.buildCount(), Long.class, builder.buildParams());
        List<Map<String, Object>> records = jdbcTemplate.queryForList(builder.buildSelect(query), builder.buildParams());

        return Result.success(new AdminPageResult(
                total != null ? total : 0, records, page, size));
    }

    @GetMapping("/actions")
    @Operation(summary = "操作类型列表")
    public Result<List<Map<String, Object>>> actionTypes() {
        List<Map<String, Object>> types = jdbcTemplate.queryForList(
                "SELECT DISTINCT action FROM admin_audit_log ORDER BY action");
        return Result.success(types);
    }
}