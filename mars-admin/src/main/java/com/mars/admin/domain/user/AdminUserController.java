package com.mars.admin.domain.user;

import com.mars.admin.common.AdminAudit;
import com.mars.admin.common.AdminPageResult;
import com.mars.admin.common.AdminQueryDTO;
import com.mars.admin.common.AdminQueryBuilder;
import com.mars.common.Result;
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
@RequestMapping("/admin/users")
@Tag(name = "用户管理", description = "管理员对用户的增删改查")
public class AdminUserController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping
    @Operation(summary = "用户列表", description = "分页、搜索、多维度筛选")
    public Result<AdminPageResult> list(
            @Parameter(description = "页码") @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(value = "size", defaultValue = "20") int size,
            @Parameter(description = "搜索关键词(用户名/昵称)") @RequestParam(value = "keyword", required = false) String keyword,
            @Parameter(description = "状态筛选(1正常/0封禁)") @RequestParam(value = "status", required = false) Integer status,
            @Parameter(description = "性别(0未知/1男/2女)") @RequestParam(value = "gender", required = false) Integer gender,
            @Parameter(description = "最小粉丝数") @RequestParam(value = "minFollowers", required = false) Integer minFollowers,
            @Parameter(description = "最大粉丝数") @RequestParam(value = "maxFollowers", required = false) Integer maxFollowers,
            @Parameter(description = "最小发帖数") @RequestParam(value = "minPosts", required = false) Integer minPosts,
            @Parameter(description = "最大发帖数") @RequestParam(value = "maxPosts", required = false) Integer maxPosts,
            @Parameter(description = "注册开始时间") @RequestParam(value = "startTime", required = false) String startTime,
            @Parameter(description = "注册结束时间") @RequestParam(value = "endTime", required = false) String endTime,
            @Parameter(description = "排序字段") @RequestParam(value = "orderBy", defaultValue = "id") String orderBy,
            @Parameter(description = "排序方向") @RequestParam(value = "orderDir", defaultValue = "DESC") String orderDir) {

        AdminQueryBuilder builder = AdminQueryBuilder.from("user u",
                "u.id, u.username, u.email, p.nickname, p.avatar_url, p.bio, " +
                        "p.follower_count, p.following_count, p.status AS profile_status, p.gender, p.created_at");

        builder.join("LEFT JOIN user_profile p ON u.id = p.user_id");

        // 筛选条件
        if (keyword != null && !keyword.isBlank()) {
            builder.where("(u.username LIKE ? OR p.nickname LIKE ? OR u.email LIKE ?)",
                    "%" + keyword + "%", "%" + keyword + "%", "%" + keyword + "%");
        }
        builder.eq("p.status", status);
        builder.eq("p.gender", gender);
        builder.range("p.follower_count", minFollowers, maxFollowers);

        // 发帖数筛选（子查询）
        if (minPosts != null) {
            builder.where("(SELECT COUNT(*) FROM post WHERE user_id = u.id) >= ?", minPosts);
        }
        if (maxPosts != null) {
            builder.where("(SELECT COUNT(*) FROM post WHERE user_id = u.id) <= ?", maxPosts);
        }

        // 时间范围
        if (startTime != null && !startTime.isBlank()) {
            builder.where("p.created_at >= ?", java.time.LocalDateTime.parse(startTime.replace(" ", "T")));
        }
        if (endTime != null && !endTime.isBlank()) {
            builder.where("p.created_at < ?", java.time.LocalDateTime.parse(endTime.replace(" ", "T")));
        }

        // 排序白名单
        String safeOrderBy = switch (orderBy) {
            case "follower_count" -> "p.follower_count";
            case "following_count" -> "p.following_count";
            case "created_at" -> "p.created_at";
            case "username" -> "u.username";
            default -> "u.id";
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

    @GetMapping("/{id}")
    @Operation(summary = "用户详情")
    public Result<Map<String, Object>> detail(@PathVariable("id") Long userId) {
        List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "SELECT u.id, u.username, u.email, p.nickname, p.avatar_url, p.bio, p.gender, " +
                        "p.birthday, p.follower_count, p.following_count, p.total_liked_count, " +
                        "p.status, p.created_at, p.updated_at " +
                        "FROM user u LEFT JOIN user_profile p ON u.id = p.user_id WHERE u.id = ?", userId);

        if (list.isEmpty()) {
            return Result.fail("用户不存在");
        }

        Long postCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM post WHERE user_id = ?", Long.class, userId);

        Long commentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM comment WHERE user_id = ? AND deleted_at IS NULL", Long.class, userId);

        Map<String, Object> data = list.get(0);
        data.put("postCount", postCount != null ? postCount : 0);
        data.put("commentCount", commentCount != null ? commentCount : 0);
        return Result.success(data);
    }

    @PutMapping("/{id}/status")
    @AdminAudit(action = "ban_user", targetType = "user", description = "修改用户状态")
    @Operation(summary = "修改用户状态", description = "正常/封禁，支持封禁天数")
    public Result<Void> updateStatus(@PathVariable("id") Long userId, @RequestBody Map<String, Object> body) {
        Integer status = (Integer) body.get("status");
        if (status == null) {
            return Result.fail("状态不能为空");
        }

        Number banDaysNum = (Number) body.get("banDays");
        int banDays = banDaysNum != null ? banDaysNum.intValue() : 0;

        if (status == 0) {
            // 封禁：设置 ban_until，隐藏用户所有帖子
            String banUntil = null;
            if (banDays > 0) {
                banUntil = java.time.LocalDateTime.now().plusDays(banDays)
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            }
            int rows = jdbcTemplate.update(
                    "UPDATE user_profile SET status = 0, ban_until = ? WHERE user_id = ?",
                    banUntil, userId);
            if (rows == 0) {
                return Result.fail("用户不存在");
            }
            // 隐藏该用户所有已发布帖子
            jdbcTemplate.update(
                    "UPDATE post SET display_status = 2 WHERE user_id = ? AND display_status = 1 AND deleted_at IS NULL",
                    userId);
            return Result.successMessage(banDays > 0 ? "用户已封禁" + banDays + "天" : "用户已永久封禁");
        } else {
            // 解封：清除 ban_until
            int rows = jdbcTemplate.update(
                    "UPDATE user_profile SET status = 1, ban_until = NULL WHERE user_id = ?",
                    userId);
            if (rows == 0) {
                return Result.fail("用户不存在");
            }
            return Result.successMessage("用户已解封");
        }
    }

    @DeleteMapping("/{id}")
    @AdminAudit(action = "delete_user", targetType = "user", description = "注销用户")
    @Operation(summary = "注销用户")
    public Result<Void> delete(
            @RequestHeader(value = "X-User-Id", required = false) String adminId,
            @PathVariable("id") Long userId) {
        Long adminUserId = adminId != null ? Long.parseLong(adminId) : null;
        jdbcTemplate.update("DELETE FROM user_relation WHERE follower_id = ? OR followed_id = ?", userId, userId);
        jdbcTemplate.update("DELETE FROM post_like WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM post_favorite WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM post_browse_history WHERE user_id = ?", userId);
        // 帖子和评论改为软删除（保留证据）
        jdbcTemplate.update("UPDATE comment SET deleted_at = NOW(), deleted_by = ? WHERE user_id = ? AND deleted_at IS NULL", adminUserId, userId);
        jdbcTemplate.update("UPDATE post SET deleted_at = NOW(), deleted_by = ?, display_status = 2 WHERE user_id = ? AND deleted_at IS NULL", adminUserId, userId);
        jdbcTemplate.update("DELETE FROM user_profile WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM user WHERE id = ?", userId);
        return Result.successMessage("用户已注销");
    }

    @GetMapping("/{id}/posts")
    @Operation(summary = "用户发帖列表", description = "查询指定用户的帖子，分页")
    public Result<AdminPageResult> getUserPosts(
            @PathVariable("id") Long userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {

        AdminQueryBuilder builder = AdminQueryBuilder.from("post",
                "id, title, summary, audit_status, display_status, like_count, comment_count, create_time");
        builder.where("user_id = ?", userId);
        builder.where("deleted_at IS NULL");
        builder.orderBy("create_time DESC");

        AdminQueryDTO query = new AdminQueryDTO();
        query.setPage(page);
        query.setSize(size);

        Long total = jdbcTemplate.queryForObject(builder.buildCount(), Long.class, builder.buildParams());
        List<Map<String, Object>> records = jdbcTemplate.queryForList(builder.buildSelect(query), builder.buildParams());

        return Result.success(new AdminPageResult(
                total != null ? total : 0, records, page, size));
    }

    @GetMapping("/{id}/comments")
    @Operation(summary = "用户评论列表", description = "查询指定用户的评论，分页")
    public Result<AdminPageResult> getUserComments(
            @PathVariable("id") Long userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {

        String countSql = "SELECT COUNT(*) FROM comment c WHERE c.user_id = ? AND c.deleted_at IS NULL";
        String selectSql = "SELECT c.id, c.post_id, c.content, c.create_time, p.title AS post_title " +
                "FROM comment c LEFT JOIN post p ON c.post_id = p.id " +
                "WHERE c.user_id = ? AND c.deleted_at IS NULL ORDER BY c.create_time DESC LIMIT ? OFFSET ?";

        Long total = jdbcTemplate.queryForObject(countSql, Long.class, userId);
        List<Map<String, Object>> records = jdbcTemplate.queryForList(selectSql, userId, size, (page - 1) * size);

        return Result.success(new AdminPageResult(
                total != null ? total : 0, records, page, size));
    }

    @GetMapping("/{id}/violations")
    @Operation(summary = "用户违规记录", description = "查询用户的封禁历史和举报记录")
    public Result<Map<String, Object>> getUserViolations(@PathVariable("id") Long userId) {
        // 查询该用户帖子/评论被举报的记录
        List<Map<String, Object>> reports = jdbcTemplate.queryForList(
                "SELECT r.id, r.target_type AS type, r.reason, r.description, " +
                        "CASE WHEN r.status = 0 THEN '待处理' WHEN r.status = 1 THEN '已处理' ELSE '已忽略' END AS action, " +
                        "r.handler_name AS operator, r.created_at " +
                        "FROM report r " +
                        "WHERE (r.target_type = 'user' AND r.target_id = ?) " +
                        "   OR (r.target_type = 'post' AND r.target_id IN (SELECT id FROM post WHERE user_id = ?)) " +
                        "   OR (r.target_type = 'comment' AND r.target_id IN (SELECT id FROM comment WHERE user_id = ?)) " +
                        "ORDER BY r.created_at DESC LIMIT 50",
                userId, userId, userId);

        Map<String, Object> data = new HashMap<>();
        data.put("records", reports);
        data.put("total", reports.size());
        return Result.success(data);
    }
}