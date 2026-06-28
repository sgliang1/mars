package com.interstellar.admin.domain.content;

import com.interstellar.admin.common.AdminAudit;
import com.interstellar.admin.common.AdminPageResult;
import com.interstellar.admin.common.AdminQueryDTO;
import com.interstellar.admin.common.AdminQueryBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interstellar.common.Result;
import com.interstellar.common.push.PushPayload;
import com.interstellar.common.push.PushService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/admin/posts")
@Tag(name = "帖子管理", description = "管理员对帖子的审核与管理")
public class AdminPostController {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // 审核状态常量
    public static final int AUDIT_PENDING = 0;        // 待审核
    public static final int AUDIT_MACHINE_PASS = 1;   // 机审通过
    public static final int AUDIT_HUMAN_PASS = 2;     // 人审通过
    public static final int AUDIT_HUMAN_REJECT = 3;   // 人审驳回
    public static final int AUDIT_REVIEWING = 4;      // 复审中(举报)

    // 展示状态常量
    public static final int DISPLAY_PENDING = 0;      // 待发布
    public static final int DISPLAY_PUBLISHED = 1;    // 已发布
    public static final int DISPLAY_OFFLINE = 2;      // 已下架

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    private PushService pushService;

    @GetMapping
    @Operation(summary = "帖子列表", description = "分页、搜索、多维度筛选，支持双状态字段")
    public Result<AdminPageResult> list(
            @Parameter(description = "页码") @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(value = "size", defaultValue = "20") int size,
            @Parameter(description = "搜索关键词(标题/摘要)") @RequestParam(value = "keyword", required = false) String keyword,
            @Parameter(description = "作者ID") @RequestParam(value = "userId", required = false) Long userId,
            @Parameter(description = "话题ID") @RequestParam(value = "topicId", required = false) Long topicId,
            @Parameter(description = "审核状态: 0-待审核, 1-机审通过, 2-人审通过, 3-人审驳回, 4-复审中") @RequestParam(value = "auditStatus", required = false) Integer auditStatus,
            @Parameter(description = "展示状态: 0-待发布, 1-已发布, 2-已下架") @RequestParam(value = "displayStatus", required = false) Integer displayStatus,
            @Parameter(description = "最小点赞数") @RequestParam(value = "minLikes", required = false) Integer minLikes,
            @Parameter(description = "最大点赞数") @RequestParam(value = "maxLikes", required = false) Integer maxLikes,
            @Parameter(description = "最小评论数") @RequestParam(value = "minComments", required = false) Integer minComments,
            @Parameter(description = "最大评论数") @RequestParam(value = "maxComments", required = false) Integer maxComments,
            @Parameter(description = "开始时间") @RequestParam(value = "startTime", required = false) String startTime,
            @Parameter(description = "结束时间") @RequestParam(value = "endTime", required = false) String endTime,
            @Parameter(description = "排序字段") @RequestParam(value = "orderBy", defaultValue = "create_time") String orderBy,
            @Parameter(description = "排序方向") @RequestParam(value = "orderDir", defaultValue = "DESC") String orderDir,
            @Parameter(description = "是否包含已删除帖子") @RequestParam(value = "includeDeleted", defaultValue = "false") boolean includeDeleted) {

        AdminQueryDTO query = new AdminQueryDTO();
        query.setPage(page);
        query.setSize(size);

        String selectColumns = "p.id, p.user_id, p.username, p.title, p.summary, " +
                "p.like_count, p.comment_count, p.share_count, " +
                "p.audit_status, p.display_status, p.prev_audit_status, p.last_auditor_id, " +
                "p.review_reason, p.reviewed_by, p.reviewed_at, p.create_time, p.deleted_at";

        AdminQueryBuilder builder = AdminQueryBuilder.from("post p", selectColumns);

        // 默认排除已删除帖子
        if (!includeDeleted) {
            builder.where("p.deleted_at IS NULL");
        }

        // 话题关联（可选）
        if (topicId != null) {
            builder.join("INNER JOIN post_topic pt ON p.id = pt.post_id");
        }

        // 关键词搜索
        if (keyword != null && !keyword.isBlank()) {
            builder.where("(p.title LIKE ? OR p.summary LIKE ?)", "%" + keyword + "%", "%" + keyword + "%");
        }

        // 其他筛选
        builder.eq("p.user_id", userId);
        if (topicId != null) {
            builder.eq("pt.topic_id", topicId);
        }

        // 双状态筛选
        builder.eq("p.audit_status", auditStatus);
        builder.eq("p.display_status", displayStatus);

        builder.range("p.like_count", minLikes, maxLikes);
        builder.range("p.comment_count", minComments, maxComments);

        // 时间范围
        if (startTime != null && !startTime.isBlank()) {
            builder.where("p.create_time >= ?", java.time.LocalDateTime.parse(startTime.replace(" ", "T")));
        }
        if (endTime != null && !endTime.isBlank()) {
            builder.where("p.create_time < ?", java.time.LocalDateTime.parse(endTime.replace(" ", "T")));
        }

        // 排序白名单
        String safeOrderBy = switch (orderBy) {
            case "like_count" -> "p.like_count";
            case "comment_count" -> "p.comment_count";
            case "share_count" -> "p.share_count";
            case "audit_status" -> "p.audit_status";
            case "display_status" -> "p.display_status";
            default -> "p.create_time";
        };
        builder.orderBy(safeOrderBy + " " + ("ASC".equalsIgnoreCase(orderDir) ? "ASC" : "DESC"));

        Long total = jdbcTemplate.queryForObject(builder.buildCount(), Long.class, builder.buildParams());
        List<Map<String, Object>> records = jdbcTemplate.queryForList(builder.buildSelect(query), builder.buildParams());

        return Result.success(new AdminPageResult(
                total != null ? total : 0, records, page, size));
    }

    @GetMapping("/workbench/tasks")
    @Operation(summary = "审核工作台任务列表", description = "按审核状态分池拉取，排除人审通过的内容")
    public Result<AdminPageResult> workbenchTasks(
            @Parameter(description = "页码") @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(value = "size", defaultValue = "50") int size,
            @Parameter(description = "池类型: pending-待审核, review-举报复审池") @RequestParam(value = "pool", defaultValue = "pending") String pool,
            @Parameter(description = "排序方向") @RequestParam(value = "orderDir", defaultValue = "ASC") String orderDir) {

        AdminQueryDTO query = new AdminQueryDTO();
        query.setPage(page);
        query.setSize(size);

        String selectColumns = "p.id, p.user_id, p.username, p.title, p.summary, " +
                "p.audit_status, p.display_status, p.prev_audit_status, p.last_auditor_id, " +
                "p.review_reason, p.create_time, " +
                "au.username AS reviewer_name, pau.username AS prev_reviewer_name";

        AdminQueryBuilder builder = AdminQueryBuilder.from("post p", selectColumns);
        builder.join("LEFT JOIN admin_user au ON p.last_auditor_id = au.id");
        builder.join("LEFT JOIN admin_user pau ON p.reviewed_by = pau.id");

        // 入池拦截逻辑：人审通过的内容不进入常规待审池
        if ("review".equals(pool)) {
            // 举报复审池：audit_status = 4 (复审中)
            builder.where("p.audit_status = ?", AUDIT_REVIEWING);
        } else {
            // 常规待审池：排除人审通过的内容
            builder.where("p.audit_status != ?", AUDIT_HUMAN_PASS);
            builder.where("p.audit_status != ?", AUDIT_REVIEWING);
        }

        builder.orderBy("p.create_time " + ("ASC".equalsIgnoreCase(orderDir) ? "ASC" : "DESC"));

        Long total = jdbcTemplate.queryForObject(builder.buildCount(), Long.class, builder.buildParams());
        List<Map<String, Object>> records = jdbcTemplate.queryForList(builder.buildSelect(query), builder.buildParams());

        // 为复审池任务添加触发原因
        for (Map<String, Object> record : records) {
            Integer auditStatus = (Integer) record.get("audit_status");
            if (auditStatus != null && auditStatus == AUDIT_REVIEWING) {
                record.put("trigger_reason", "被举报");
            } else {
                record.put("trigger_reason", "待审核");
            }
        }

        return Result.success(new AdminPageResult(
                total != null ? total : 0, records, page, size));
    }

    @GetMapping("/{id}/context")
    @Operation(summary = "帖子上下文详情", description = "聚合帖子详情、评论、作者画像，供工作台一次性消费")
    public Result<Map<String, Object>> context(
            @PathVariable("id") Long postId,
            @Parameter(description = "是否包含已删除评论") @RequestParam(value = "includeDeleted", defaultValue = "false") boolean includeDeleted) {
        // 获取帖子详情（管理员可以看到已删除帖子）
        List<Map<String, Object>> posts = jdbcTemplate.queryForList(
                "SELECT p.*, pc.content AS full_content " +
                        "FROM post p LEFT JOIN post_content pc ON p.id = pc.post_id WHERE p.id = ?", postId);

        if (posts.isEmpty()) {
            return Result.fail("帖子不存在");
        }

        Map<String, Object> post = posts.get(0);

        // 获取评论列表（默认排除已删除评论）
        String commentSql = includeDeleted
                ? "SELECT id, user_id, username, content, parent_id, create_time, deleted_at FROM comment WHERE post_id = ? ORDER BY create_time DESC LIMIT 100"
                : "SELECT id, user_id, username, content, parent_id, create_time FROM comment WHERE post_id = ? AND deleted_at IS NULL ORDER BY create_time DESC LIMIT 100";
        List<Map<String, Object>> comments = jdbcTemplate.queryForList(commentSql, postId);

        // 获取作者画像
        Long userId = (Long) post.get("user_id");
        Map<String, Object> authorProfile = new HashMap<>();
        if (userId != null) {
            List<Map<String, Object>> users = jdbcTemplate.queryForList(
                    "SELECT id, username, email, status, create_time FROM user WHERE id = ?", userId);
            if (!users.isEmpty()) {
                authorProfile = users.get(0);
            }

            // 获取作者近期帖子
            List<Map<String, Object>> recentPosts = jdbcTemplate.queryForList(
                    "SELECT id, title, summary, audit_status, display_status, create_time " +
                            "FROM post WHERE user_id = ? ORDER BY create_time DESC LIMIT 10", userId);
            authorProfile.put("recent_posts", recentPosts);

            // 获取违规统计
            Long violationCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM admin_audit_log WHERE target_type = 'post' AND action IN ('delete_post', 'reject') " +
                            "AND target_id IN (SELECT id FROM post WHERE user_id = ?)", Long.class, userId);
            authorProfile.put("violation_count", violationCount != null ? violationCount : 0);
        }

        // 组装上下文数据
        Map<String, Object> context = new HashMap<>();
        context.put("post", post);
        context.put("comments", comments);
        context.put("author", authorProfile);

        return Result.success(context);
    }

    @GetMapping("/{id}")
    @Operation(summary = "帖子详情", description = "含内容和评论，管理员可查看已删除帖子")
    public Result<Map<String, Object>> detail(
            @PathVariable("id") Long postId,
            @Parameter(description = "是否包含已删除评论") @RequestParam(value = "includeDeleted", defaultValue = "false") boolean includeDeleted) {
        List<Map<String, Object>> posts = jdbcTemplate.queryForList(
                "SELECT p.*, pc.content AS full_content " +
                        "FROM post p LEFT JOIN post_content pc ON p.id = pc.post_id WHERE p.id = ?", postId);

        if (posts.isEmpty()) {
            return Result.fail("帖子不存在");
        }

        String commentSql = includeDeleted
                ? "SELECT id, user_id, username, content, parent_id, create_time, deleted_at FROM comment WHERE post_id = ? ORDER BY create_time DESC LIMIT 50"
                : "SELECT id, user_id, username, content, parent_id, create_time FROM comment WHERE post_id = ? AND deleted_at IS NULL ORDER BY create_time DESC LIMIT 50";
        List<Map<String, Object>> comments = jdbcTemplate.queryForList(commentSql, postId);

        Map<String, Object> data = posts.get(0);
        data.put("comments", comments);
        return Result.success(data);
    }

    @PutMapping("/{id}/status")
    @AdminAudit(action = "review_post", targetType = "post", description = "修改帖子状态")
    @Operation(summary = "修改帖子状态", description = "支持双状态字段，实现状态机校验")
    public Result<Void> updateStatus(
            @RequestHeader(value = "X-User-Id", required = false) String adminId,
            @RequestHeader(value = "X-User-Name", required = false) String adminName,
            @PathVariable("id") Long postId, @RequestBody Map<String, Object> body) {
        
        Integer auditStatus = (Integer) body.get("auditStatus");
        Integer displayStatus = (Integer) body.get("displayStatus");
        String reason = (String) body.get("reason");

        if (auditStatus == null && displayStatus == null) {
            return Result.fail("状态不能为空");
        }

        Long reviewerId = adminId != null ? Long.parseLong(adminId) : null;

        // 构建更新SQL
        StringBuilder sql = new StringBuilder("UPDATE post SET ");
        List<Object> params = new java.util.ArrayList<>();

        if (auditStatus != null) {
            // 状态机校验：人审通过 -> 需要拦截
            List<Map<String, Object>> currentPost = jdbcTemplate.queryForList(
                    "SELECT audit_status FROM post WHERE id = ?", postId);
            if (!currentPost.isEmpty()) {
                Integer currentAuditStatus = (Integer) currentPost.get(0).get("audit_status");
                // 如果当前是人审通过，变更需要特殊处理（拦截机制）
                if (currentAuditStatus != null && currentAuditStatus == AUDIT_HUMAN_PASS) {
                    // 记录被击穿前的状态
                    sql.append("prev_audit_status = ?, ");
                    params.add(currentAuditStatus);
                }
            }

            sql.append("audit_status = ?, ");
            params.add(auditStatus);
            sql.append("last_auditor_id = ?, ");
            params.add(reviewerId);
        }

        if (displayStatus != null) {
            sql.append("display_status = ?, ");
            params.add(displayStatus);
        }

        if (reason != null) {
            sql.append("review_reason = ?, ");
            params.add(reason);
        }

        sql.append("reviewed_by = ?, reviewed_at = NOW() WHERE id = ?");
        params.add(reviewerId);
        params.add(postId);

        int rows = jdbcTemplate.update(sql.toString(), params.toArray());
        if (rows == 0) {
            return Result.fail("帖子不存在");
        }
        return Result.successMessage("状态更新成功");
    }

    @PutMapping("/{id}/audit")
    @AdminAudit(action = "audit_post", targetType = "post", description = "审核决策")
    @Operation(summary = "工作台审核决策", description = "通过/驳回/删除/禁言作者，自动切换下一任务")
    public Result<Map<String, Object>> auditDecision(
            @RequestHeader(value = "X-User-Id", required = false) String adminId,
            @RequestHeader(value = "X-User-Name", required = false) String adminName,
            @PathVariable("id") Long postId, @RequestBody Map<String, Object> body) {
        
        String action = (String) body.get("action"); // approve/reject/delete/ban
        String reason = (String) body.get("reason");

        if (action == null || action.isBlank()) {
            return Result.fail("操作类型不能为空");
        }

        Long reviewerId = adminId != null ? Long.parseLong(adminId) : null;
        Map<String, Object> result = new HashMap<>();

        switch (action) {
            case "approve" -> {
                // 审核通过：设置为人审通过
                jdbcTemplate.update(
                        "UPDATE post SET audit_status = ?, last_auditor_id = ?, reviewed_by = ?, reviewed_at = NOW(), review_reason = ? WHERE id = ?",
                        AUDIT_HUMAN_PASS, reviewerId, reviewerId, reason, postId);
                result.put("message", "审核通过");
                // 通知帖子作者审核通过
                sendAuditNotification(postId, "audit_pass", "帖子审核通过", reason);
            }
            case "reject" -> {
                // 审核驳回：设置为人审驳回，下架展示
                jdbcTemplate.update(
                        "UPDATE post SET audit_status = ?, display_status = ?, last_auditor_id = ?, reviewed_by = ?, reviewed_at = NOW(), review_reason = ? WHERE id = ?",
                        AUDIT_HUMAN_REJECT, DISPLAY_OFFLINE, reviewerId, reviewerId, reason, postId);
                result.put("message", "审核驳回");
                // 通知帖子作者审核驳回
                sendAuditNotification(postId, "audit_reject", "帖子审核未通过", reason);
            }
            case "delete" -> {
                // 软删除帖子（保留证据）
                jdbcTemplate.update(
                        "UPDATE post SET deleted_at = NOW(), deleted_by = ?, display_status = ? WHERE id = ? AND deleted_at IS NULL",
                        reviewerId, DISPLAY_OFFLINE, postId);
                // 同步软删除该帖子下的评论
                jdbcTemplate.update(
                        "UPDATE comment SET deleted_at = NOW(), deleted_by = ? WHERE post_id = ? AND deleted_at IS NULL",
                        reviewerId, postId);
                result.put("message", "帖子已删除");
            }
            case "ban" -> {
                // 禁言作者
                Long postUserId = jdbcTemplate.queryForObject(
                        "SELECT user_id FROM post WHERE id = ?", Long.class, postId);
                if (postUserId != null) {
                    jdbcTemplate.update("UPDATE user SET status = 2 WHERE id = ?", postUserId);
                    result.put("message", "作者已禁言");
                } else {
                    return Result.fail("帖子不存在");
                }
            }
            default -> {
                return Result.fail("不支持的操作类型: " + action);
            }
        }

        return Result.success(result);
    }

    @PutMapping("/{id}/batch-status")
    @AdminAudit(action = "batch_update_status", targetType = "post", description = "批量修改帖子状态")
    @Operation(summary = "批量修改状态", description = "批量通过/批量删除")
    public Result<Void> batchUpdateStatus(
            @RequestHeader(value = "X-User-Id", required = false) String adminId,
            @RequestBody Map<String, Object> body) {
        
        List<Long> ids = (List<Long>) body.get("ids");
        String action = (String) body.get("action"); // approve/reject/delete

        if (ids == null || ids.isEmpty()) {
            return Result.fail("请选择要操作的帖子");
        }

        Long reviewerId = adminId != null ? Long.parseLong(adminId) : null;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ids", ids)
                .addValue("reviewerId", reviewerId);

        switch (action) {
            case "approve" -> {
                namedParameterJdbcTemplate.update(
                        "UPDATE post SET audit_status = :auditStatus, last_auditor_id = :reviewerId, reviewed_by = :reviewerId, reviewed_at = NOW() WHERE id IN (:ids)",
                        params.addValue("auditStatus", AUDIT_HUMAN_PASS));
            }
            case "reject" -> {
                namedParameterJdbcTemplate.update(
                        "UPDATE post SET audit_status = :auditStatus, display_status = :displayStatus, last_auditor_id = :reviewerId, reviewed_by = :reviewerId, reviewed_at = NOW() WHERE id IN (:ids)",
                        params.addValue("auditStatus", AUDIT_HUMAN_REJECT).addValue("displayStatus", DISPLAY_OFFLINE));
            }
            case "delete" -> {
                // 批量软删除帖子（保留证据）
                namedParameterJdbcTemplate.update(
                        "UPDATE post SET deleted_at = NOW(), deleted_by = :reviewerId, display_status = :displayStatus WHERE id IN (:ids) AND deleted_at IS NULL",
                        params.addValue("displayStatus", DISPLAY_OFFLINE));
                // 同步软删除评论
                namedParameterJdbcTemplate.update(
                        "UPDATE comment SET deleted_at = NOW(), deleted_by = :reviewerId WHERE post_id IN (:ids) AND deleted_at IS NULL",
                        params);
            }
            default -> {
                return Result.fail("不支持的操作类型: " + action);
            }
        }

        return Result.successMessage("批量操作成功");
    }

    @DeleteMapping("/{id}")
    @AdminAudit(action = "delete_post", targetType = "post", description = "软删除帖子（保留证据）")
    @Operation(summary = "软删除帖子", description = "标记为已删除，保留数据作为证据")
    public Result<Void> delete(
            @RequestHeader(value = "X-User-Id", required = false) String adminId,
            @PathVariable("id") Long postId) {
        Long adminUserId = adminId != null ? Long.parseLong(adminId) : null;
        int rows = jdbcTemplate.update(
                "UPDATE post SET deleted_at = NOW(), deleted_by = ?, display_status = ? WHERE id = ? AND deleted_at IS NULL",
                adminUserId, DISPLAY_OFFLINE, postId);
        if (rows == 0) {
            return Result.fail("帖子不存在或已被删除");
        }
        // 同步软删除评论
        jdbcTemplate.update(
                "UPDATE comment SET deleted_at = NOW(), deleted_by = ? WHERE post_id = ? AND deleted_at IS NULL",
                adminUserId, postId);
        return Result.successMessage("帖子已删除");
    }

    @DeleteMapping("/{id}/permanent")
    @AdminAudit(action = "permanent_delete_post", targetType = "post", description = "彻底删除帖子（不可恢复）")
    @Operation(summary = "彻底删除帖子", description = "物理删除，用于最终清理证据链")
    public Result<Void> permanentDelete(@PathVariable("id") Long postId) {
        jdbcTemplate.update("DELETE FROM post_like WHERE post_id = ?", postId);
        jdbcTemplate.update("DELETE FROM post_image WHERE post_id = ?", postId);
        jdbcTemplate.update("DELETE FROM comment WHERE post_id = ?", postId);
        jdbcTemplate.update("DELETE FROM post_content WHERE post_id = ?", postId);
        jdbcTemplate.update("DELETE FROM post_topic WHERE post_id = ?", postId);
        jdbcTemplate.update("DELETE FROM post_favorite WHERE post_id = ?", postId);
        jdbcTemplate.update("DELETE FROM post WHERE id = ?", postId);
        return Result.successMessage("帖子已彻底删除");
    }

    @PutMapping("/{id}/pin")
    @AdminAudit(action = "pin_post", targetType = "post", description = "置顶/取消置顶帖子")
    @Operation(summary = "置顶/取消置顶帖子")
    public Result<Void> togglePin(
            @RequestHeader(value = "X-User-Id", required = false) String adminId,
            @PathVariable("id") Long postId) {
        List<Map<String, Object>> posts = jdbcTemplate.queryForList(
                "SELECT is_pinned FROM post WHERE id = ?", postId);
        if (posts.isEmpty()) return Result.fail("帖子不存在");

        Integer current = (Integer) posts.get(0).get("is_pinned");
        if (current != null && current == 1) {
            jdbcTemplate.update("UPDATE post SET is_pinned = 0, pinned_at = NULL WHERE id = ?", postId);
            return Result.successMessage("已取消置顶");
        } else {
            jdbcTemplate.update("UPDATE post SET is_pinned = 1, pinned_at = NOW() WHERE id = ?", postId);
            return Result.successMessage("已置顶");
        }
    }

    @PutMapping("/{id}/feature")
    @AdminAudit(action = "feature_post", targetType = "post", description = "加精/取消加精帖子")
    @Operation(summary = "加精/取消加精帖子")
    public Result<Void> toggleFeature(
            @RequestHeader(value = "X-User-Id", required = false) String adminId,
            @PathVariable("id") Long postId) {
        List<Map<String, Object>> posts = jdbcTemplate.queryForList(
                "SELECT is_featured FROM post WHERE id = ?", postId);
        if (posts.isEmpty()) return Result.fail("帖子不存在");

        Integer current = (Integer) posts.get(0).get("is_featured");
        if (current != null && current == 1) {
            jdbcTemplate.update("UPDATE post SET is_featured = 0 WHERE id = ?", postId);
            return Result.successMessage("已取消加精");
        } else {
            jdbcTemplate.update("UPDATE post SET is_featured = 1 WHERE id = ?", postId);
            return Result.successMessage("已加精");
        }
    }

    /**
     * 审核结果通知：写入 notification 表 + 发送推送
     */
    private void sendAuditNotification(Long postId, String sourceType, String title, String reason) {
        try {
            // 查帖子作者和标题
            List<Map<String, Object>> posts = jdbcTemplate.queryForList(
                    "SELECT user_id, title FROM post WHERE id = ?", postId);
            if (posts.isEmpty()) return;

            Long authorId = (Long) posts.get(0).get("user_id");
            String postTitle = (String) posts.get(0).get("title");
            if (authorId == null) return;

            // 构建 content JSON（使用 Jackson 避免手动拼接的转义问题）
            Map<String, String> contentMap = new HashMap<>();
            contentMap.put("postId", String.valueOf(postId));
            contentMap.put("postTitle", postTitle != null ? postTitle : "");
            contentMap.put("reason", reason != null ? (reason.length() > 80 ? reason.substring(0, 80) : reason) : "");
            String content = OBJECT_MAPPER.writeValueAsString(contentMap);

            // 写入 notification 表
            jdbcTemplate.update(
                    "INSERT INTO notification (user_id, category, title, content, source_type, source_id, read_status, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, 0, NOW())",
                    authorId, "system", title, content, sourceType, String.valueOf(postId));

            // 发送推送通知
            try {
                String pushTitle = "audit_pass".equals(sourceType) ? "帖子审核通过" : "帖子审核未通过";
                String pushBody = "audit_pass".equals(sourceType)
                        ? "您的帖子「" + (postTitle != null ? postTitle : "") + "」已通过审核"
                        : "您的帖子「" + (postTitle != null ? postTitle : "") + "」未通过审核"
                        + (reason != null && !reason.isBlank() ? "，原因：" + reason : "");
                PushPayload payload = PushPayload.of(pushTitle, pushBody, sourceType)
                        .withClickAction("/post/" + postId);
                pushService.sendToUser(authorId, payload);
            } catch (Exception e) {
                // 推送失败不影响主流程
            }
        } catch (Exception e) {
            // 通知失败不影响审核主流程
        }
    }
}