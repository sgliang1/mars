package com.interstellar.admin.domain.moderation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interstellar.api.UserFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AdminReportService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private UserFeignClient userFeignClient;

    /**
     * 处理举报（确认违规/忽略，级联操作加事务保护）
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleReport(Long reportId, String action, String postAction,
                             int banDays, String reason, Long handlerId, String handlerName) {
        // 获取举报信息
        List<Map<String, Object>> reports = jdbcTemplate.queryForList(
                "SELECT * FROM report WHERE id = ?", reportId);
        if (reports.isEmpty()) {
            throw new IllegalArgumentException("举报不存在");
        }

        Map<String, Object> report = reports.get(0);
        String targetType = (String) report.get("target_type");
        Long targetId = ((Number) report.get("target_id")).longValue();

        if ("confirm_violation".equals(action)) {
            handleConfirmViolation(reportId, targetType, targetId, postAction,
                    banDays, reason, handlerId, handlerName);

            // 举报核实：举报人声望 +5
            try {
                Long reporterId = ((Number) report.get("reporter_id")).longValue();
                userFeignClient.addReputation(java.util.Map.of(
                        "userId", reporterId,
                        "amount", 5,
                        "sourceType", "report_confirmed",
                        "sourceId", reportId,
                        "description", "举报核实通过"));
            } catch (Exception ignored) {}
        } else {
            handleIgnore(reportId, targetType, targetId, reason, handlerId);
        }

        // 通知举报人处理结果
        notifyReporter(report, action, reason);

        log.info("举报处理完成: reportId={}, action={}, handlerId={}", reportId, action, handlerId);
    }

    /**
     * 向举报人发送处理结果通知
     */
    private void notifyReporter(Map<String, Object> report, String action, String reason) {
        try {
            Long reporterId = ((Number) report.get("reporter_id")).longValue();
            String targetType = (String) report.get("target_type");

            String actionText = "confirm_violation".equals(action) ? "已确认违规" : "已忽略";
            String resultText = reason != null && !reason.isBlank() ? reason : actionText;

            Map<String, String> contentMap = new HashMap<>();
            contentMap.put("reportId", String.valueOf(report.get("id")));
            contentMap.put("targetType", targetType);
            contentMap.put("action", action);
            contentMap.put("result", resultText);
            String contentJson = OBJECT_MAPPER.writeValueAsString(contentMap);

            String title = "您的举报已处理：" + actionText;

            jdbcTemplate.update(
                    "INSERT INTO notification (user_id, category, title, content, source_type, source_id, read_status, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, 0, NOW())",
                    reporterId, "interaction", title, contentJson, "report_result", String.valueOf(report.get("id")));
        } catch (Exception e) {
            log.warn("发送举报处理通知失败", e);
        }
    }

    private void handleConfirmViolation(Long reportId, String targetType, Long targetId,
                                         String postAction, int banDays, String reason,
                                         Long handlerId, String handlerName) {
        // 1. 处理被举报内容
        Long targetUserId = null;
        if ("post".equals(targetType)) {
            if (postAction == null) throw new IllegalArgumentException("帖子处理方式不能为空");
            targetUserId = getPostUserId(targetId);
            if ("delete".equals(postAction)) {
                jdbcTemplate.update(
                        "UPDATE post SET deleted_at = NOW(), display_status = 2, reviewed_by = ?, reviewed_at = NOW() WHERE id = ?",
                        handlerId, targetId);
            } else {
                jdbcTemplate.update(
                        "UPDATE post SET display_status = 2, reviewed_by = ?, reviewed_at = NOW() WHERE id = ?",
                        handlerId, targetId);
            }
        } else if ("comment".equals(targetType)) {
            if (postAction == null) throw new IllegalArgumentException("评论处理方式不能为空");
            targetUserId = getCommentUserId(targetId);
            jdbcTemplate.update(
                    "UPDATE comment SET deleted_at = NOW(), deleted_by = ? WHERE id = ?",
                    handlerId, targetId);
        } else if ("user".equals(targetType)) {
            targetUserId = targetId;
        }

        // 2. 封禁用户（可选）
        if (banDays > 0 && targetUserId != null) {
            String banUntil = LocalDateTime.now().plusDays(banDays)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            jdbcTemplate.update(
                    "UPDATE user_profile SET status = 0, ban_until = ? WHERE user_id = ?",
                    banUntil, targetUserId);
            jdbcTemplate.update(
                    "UPDATE post SET display_status = 2 WHERE user_id = ? AND display_status = 1 AND deleted_at IS NULL",
                    targetUserId);
        }

        // 3. 更新举报状态
        String resultText = reason != null ? reason : "确认违规";
        if (banDays > 0) {
            resultText += "（封禁" + banDays + "天）";
        }
        jdbcTemplate.update(
                "UPDATE report SET status = 1, handler_id = ?, handle_result = ?, handled_at = NOW() WHERE id = ?",
                handlerId, resultText, reportId);

        // 4. 审计日志
        String auditDetail = String.format("确认违规 | 帖子操作: %s | 封禁天数: %d | 原因: %s",
                postAction != null ? postAction : "-", banDays, reason != null ? reason : "-");
        jdbcTemplate.update(
                "INSERT INTO admin_audit_log (admin_id, admin_username, action, target_type, target_id, detail, created_at) " +
                        "VALUES (?, ?, 'confirm_violation', ?, ?, ?, NOW())",
                handlerId, handlerName, targetType, targetId, auditDetail);
    }

    private void handleIgnore(Long reportId, String targetType, Long targetId,
                               String reason, Long handlerId) {
        jdbcTemplate.update(
                "UPDATE report SET status = 2, handler_id = ?, handle_result = ?, handled_at = NOW() WHERE id = ?",
                handlerId, reason != null ? reason : "管理员忽略此举报", reportId);

        // 如果帖子因举报被设为"复审中"(audit_status=4)，忽略后恢复原状态
        if ("post".equals(targetType)) {
            List<Map<String, Object>> posts = jdbcTemplate.queryForList(
                    "SELECT prev_audit_status, audit_status FROM post WHERE id = ?", targetId);
            if (!posts.isEmpty()) {
                Integer auditStatus = (Integer) posts.get(0).get("audit_status");
                if (auditStatus != null && auditStatus == 4) {
                    Integer prevStatus = (Integer) posts.get(0).get("prev_audit_status");
                    int restoreStatus = prevStatus != null ? prevStatus : 2;
                    jdbcTemplate.update("UPDATE post SET audit_status = ? WHERE id = ?", restoreStatus, targetId);
                }
            }
        }
    }

    private Long getPostUserId(Long postId) {
        List<Map<String, Object>> posts = jdbcTemplate.queryForList("SELECT user_id FROM post WHERE id = ?", postId);
        return posts.isEmpty() ? null : ((Number) posts.get(0).get("user_id")).longValue();
    }

    private Long getCommentUserId(Long commentId) {
        List<Map<String, Object>> comments = jdbcTemplate.queryForList("SELECT user_id FROM comment WHERE id = ?", commentId);
        return comments.isEmpty() ? null : ((Number) comments.get(0).get("user_id")).longValue();
    }
}
