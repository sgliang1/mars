package com.mars.admin.domain;

import com.mars.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 数据导出
 * 提供 CSV 格式的数据导出功能
 */
@RestController
@RequestMapping("/admin/export")
@Tag(name = "数据导出", description = "CSV格式数据导出")
public class AdminExportController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/posts")
    @Operation(summary = "导出帖子列表")
    public void exportPosts(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "auditStatus", required = false) Integer auditStatus,
            @RequestParam(value = "displayStatus", required = false) Integer displayStatus,
            @RequestParam(value = "startTime", required = false) String startTime,
            @RequestParam(value = "endTime", required = false) String endTime,
            HttpServletResponse response) throws Exception {

        StringBuilder sql = new StringBuilder(
                "SELECT p.id, p.title, u.username, p.audit_status, p.display_status, " +
                        "p.like_count, p.comment_count, p.create_time " +
                        "FROM post p LEFT JOIN user u ON p.user_id = u.id WHERE p.deleted_at IS NULL");

        // 动态拼接筛选条件（与帖子列表一致）
        java.util.List<Object> params = new java.util.ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (p.title LIKE ? OR p.summary LIKE ?)");
            params.add("%" + keyword + "%");
            params.add("%" + keyword + "%");
        }
        if (auditStatus != null) {
            sql.append(" AND p.audit_status = ?");
            params.add(auditStatus);
        }
        if (displayStatus != null) {
            sql.append(" AND p.display_status = ?");
            params.add(displayStatus);
        }
        if (startTime != null && !startTime.isBlank()) {
            sql.append(" AND p.create_time >= ?");
            params.add(LocalDateTime.parse(startTime.replace(" ", "T")));
        }
        if (endTime != null && !endTime.isBlank()) {
            sql.append(" AND p.create_time < ?");
            params.add(LocalDateTime.parse(endTime.replace(" ", "T")));
        }
        sql.append(" ORDER BY p.create_time DESC LIMIT 10000");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());

        writeCsv(response, "posts_" + DATE_FMT.format(LocalDateTime.now()) + ".csv",
                new String[]{"ID", "标题", "作者", "审核状态", "展示状态", "点赞", "评论", "发布时间"},
                rows, row -> new String[]{
                        String.valueOf(row.get("id")),
                        (String) row.get("title"),
                        (String) row.get("username"),
                        auditStatusLabel(row.get("audit_status")),
                        displayStatusLabel(row.get("display_status")),
                        String.valueOf(row.get("like_count")),
                        String.valueOf(row.get("comment_count")),
                        String.valueOf(row.get("create_time")),
                });
    }

    @GetMapping("/users")
    @Operation(summary = "导出用户列表")
    public void exportUsers(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "startTime", required = false) String startTime,
            @RequestParam(value = "endTime", required = false) String endTime,
            HttpServletResponse response) throws Exception {

        StringBuilder sql = new StringBuilder(
                "SELECT u.id, u.username, u.email, p.nickname, p.gender, p.follower_count, " +
                        "p.status, p.created_at FROM user u LEFT JOIN user_profile p ON u.id = p.user_id WHERE 1=1");

        java.util.List<Object> params = new java.util.ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (u.username LIKE ? OR p.nickname LIKE ? OR u.email LIKE ?)");
            params.add("%" + keyword + "%");
            params.add("%" + keyword + "%");
            params.add("%" + keyword + "%");
        }
        if (status != null) {
            sql.append(" AND p.status = ?");
            params.add(status);
        }
        if (startTime != null && !startTime.isBlank()) {
            sql.append(" AND p.created_at >= ?");
            params.add(LocalDateTime.parse(startTime.replace(" ", "T")));
        }
        if (endTime != null && !endTime.isBlank()) {
            sql.append(" AND p.created_at < ?");
            params.add(LocalDateTime.parse(endTime.replace(" ", "T")));
        }
        sql.append(" ORDER BY u.id DESC LIMIT 10000");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());

        writeCsv(response, "users_" + DATE_FMT.format(LocalDateTime.now()) + ".csv",
                new String[]{"ID", "用户名", "邮箱", "昵称", "性别", "粉丝数", "状态", "注册时间"},
                rows, row -> new String[]{
                        String.valueOf(row.get("id")),
                        (String) row.get("username"),
                        (String) row.get("email"),
                        (String) row.get("nickname"),
                        genderLabel(row.get("gender")),
                        String.valueOf(row.get("follower_count")),
                        ((Number) row.get("status")).intValue() == 1 ? "正常" : "封禁",
                        String.valueOf(row.get("created_at")),
                });
    }

    @GetMapping("/reports")
    @Operation(summary = "导出举报记录")
    public void exportReports(
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "targetType", required = false) String targetType,
            @RequestParam(value = "startTime", required = false) String startTime,
            @RequestParam(value = "endTime", required = false) String endTime,
            HttpServletResponse response) throws Exception {

        StringBuilder sql = new StringBuilder(
                "SELECT r.id, r.target_type, r.target_id, r.reason, r.description, " +
                        "r.status, ru.username AS reporter, r.handle_result, r.created_at " +
                        "FROM report r LEFT JOIN user ru ON r.reporter_id = ru.id WHERE 1=1");

        java.util.List<Object> params = new java.util.ArrayList<>();
        if (status != null) {
            sql.append(" AND r.status = ?");
            params.add(status);
        }
        if (targetType != null && !targetType.isBlank()) {
            sql.append(" AND r.target_type = ?");
            params.add(targetType);
        }
        if (startTime != null && !startTime.isBlank()) {
            sql.append(" AND r.created_at >= ?");
            params.add(LocalDateTime.parse(startTime.replace(" ", "T")));
        }
        if (endTime != null && !endTime.isBlank()) {
            sql.append(" AND r.created_at < ?");
            params.add(LocalDateTime.parse(endTime.replace(" ", "T")));
        }
        sql.append(" ORDER BY r.created_at DESC LIMIT 10000");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());

        writeCsv(response, "reports_" + DATE_FMT.format(LocalDateTime.now()) + ".csv",
                new String[]{"ID", "举报类型", "目标ID", "举报原因", "描述", "状态", "举报人", "处理结果", "举报时间"},
                rows, row -> new String[]{
                        String.valueOf(row.get("id")),
                        targetTypeName(row.get("target_type")),
                        String.valueOf(row.get("target_id")),
                        (String) row.get("reason"),
                        (String) row.get("description"),
                        reportStatusLabel(row.get("status")),
                        (String) row.get("reporter"),
                        (String) row.get("handle_result"),
                        String.valueOf(row.get("created_at")),
                });
    }

    // ========== 工具方法 ==========

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    @FunctionalInterface
    private interface RowMapper {
        String[] map(Map<String, Object> row);
    }

    private void writeCsv(HttpServletResponse response, String filename, String[] headers,
                          List<Map<String, Object>> rows, RowMapper mapper) throws Exception {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=" + filename);
        // UTF-8 BOM for Excel compatibility
        response.getOutputStream().write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

        PrintWriter writer = response.getWriter();
        writer.println(String.join(",", headers));
        for (Map<String, Object> row : rows) {
            String[] values = mapper.map(row);
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < values.length; i++) {
                if (i > 0) line.append(",");
                String val = values[i] != null ? values[i].replace("\"", "\"\"") : "";
                line.append("\"").append(val).append("\"");
            }
            writer.println(line.toString());
        }
        writer.flush();
    }

    private String auditStatusLabel(Object status) {
        if (status == null) return "未知";
        return switch (((Number) status).intValue()) {
            case 0 -> "待审核";
            case 1 -> "机审通过";
            case 2 -> "人审通过";
            case 3 -> "人审驳回";
            case 4 -> "复审中";
            default -> "未知";
        };
    }

    private String displayStatusLabel(Object status) {
        if (status == null) return "未知";
        return switch (((Number) status).intValue()) {
            case 0 -> "待发布";
            case 1 -> "已发布";
            case 2 -> "已下架";
            default -> "未知";
        };
    }

    private String genderLabel(Object gender) {
        if (gender == null) return "未知";
        return switch (((Number) gender).intValue()) {
            case 1 -> "男";
            case 2 -> "女";
            default -> "未知";
        };
    }

    private String targetTypeName(Object type) {
        if (type == null) return "未知";
        return switch (type.toString()) {
            case "post" -> "帖子";
            case "comment" -> "评论";
            case "user" -> "用户";
            default -> type.toString();
        };
    }

    private String reportStatusLabel(Object status) {
        if (status == null) return "未知";
        return switch (((Number) status).intValue()) {
            case 0 -> "待处理";
            case 1 -> "已处理";
            case 2 -> "已忽略";
            default -> "未知";
        };
    }
}
