package com.mars.admin.domain.moderation;

import com.mars.admin.common.AdminPageResult;
import com.mars.admin.common.AdminQueryDTO;
import com.mars.admin.common.AdminQueryBuilder;
import com.mars.admin.common.AdminAudit;
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

/**
 * 审核策略管理
 *
 * 需要以下数据库表（需手动创建）:
 *
 * CREATE TABLE sensitive_word (
 *   id BIGINT AUTO_INCREMENT PRIMARY KEY,
 *   word VARCHAR(100) NOT NULL,
 *   category VARCHAR(50) DEFAULT 'default',
 *   created_at DATETIME DEFAULT NOW(),
 *   UNIQUE KEY uk_word (word)
 * );
 *
 * CREATE TABLE audit_rule (
 *   id BIGINT AUTO_INCREMENT PRIMARY KEY,
 *   rule_key VARCHAR(100) NOT NULL UNIQUE,
 *   rule_name VARCHAR(200) NOT NULL,
 *   rule_value TEXT,
 *   description VARCHAR(500),
 *   updated_at DATETIME DEFAULT NOW()
 * );
 *
 * INSERT INTO audit_rule (rule_key, rule_name, rule_value, description) VALUES
 *   ('new_user_review_count', '新用户审核帖数', '3', '新注册用户前N帖需要人工审核'),
 *   ('report_auto_threshold', '举报自动触发阈值', '3', '被举报N次后自动触发复审'),
 *   ('auto_delete_spam', '自动删除垃圾内容', 'false', '机审标记为垃圾内容时是否自动删除');
 */
@RestController
@RequestMapping("/admin/rules")
@Tag(name = "审核策略", description = "敏感词库和自动审核规则配置")
public class AdminRuleController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ==================== 敏感词管理 ====================

    @GetMapping("/keywords")
    @Operation(summary = "敏感词列表", description = "分页查询敏感词库")
    public Result<AdminPageResult> listKeywords(
            @Parameter(description = "页码") @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(value = "size", defaultValue = "20") int size,
            @Parameter(description = "分类筛选") @RequestParam(value = "category", required = false) String category,
            @Parameter(description = "关键词搜索") @RequestParam(value = "keyword", required = false) String keyword) {

        AdminQueryBuilder builder = AdminQueryBuilder.from("sensitive_word", "id, word, category, created_at");

        builder.eq("category", category);
        if (keyword != null && !keyword.isBlank()) {
            builder.where("word LIKE ?", "%" + keyword + "%");
        }
        builder.orderBy("created_at DESC");

        AdminQueryDTO query = new AdminQueryDTO();
        query.setPage(page);
        query.setSize(size);

        Long total = jdbcTemplate.queryForObject(builder.buildCount(), Long.class, builder.buildParams());
        List<Map<String, Object>> records = jdbcTemplate.queryForList(builder.buildSelect(query), builder.buildParams());

        return Result.success(new AdminPageResult(total != null ? total : 0, records, page, size));
    }

    @PostMapping("/keywords")
    @AdminAudit(action = "add_keyword", targetType = "rule", description = "添加敏感词")
    @Operation(summary = "添加敏感词")
    public Result<Void> addKeyword(@RequestBody Map<String, Object> body) {
        String word = (String) body.get("word");
        String category = (String) body.getOrDefault("category", "default");

        if (word == null || word.isBlank()) {
            return Result.fail("敏感词不能为空");
        }

        try {
            jdbcTemplate.update(
                    "INSERT INTO sensitive_word (word, category, created_at) VALUES (?, ?, NOW())",
                    word.trim(), category);
            return Result.successMessage("添加成功");
        } catch (Exception e) {
            return Result.fail("该敏感词已存在");
        }
    }

    @DeleteMapping("/keywords/{id}")
    @AdminAudit(action = "delete_keyword", targetType = "rule", description = "删除敏感词")
    @Operation(summary = "删除敏感词")
    public Result<Void> deleteKeyword(@PathVariable("id") Long id) {
        int rows = jdbcTemplate.update("DELETE FROM sensitive_word WHERE id = ?", id);
        if (rows == 0) {
            return Result.fail("敏感词不存在");
        }
        return Result.successMessage("删除成功");
    }

    @PostMapping("/keywords/batch")
    @AdminAudit(action = "batch_add_keywords", targetType = "rule", description = "批量添加敏感词")
    @Operation(summary = "批量添加敏感词", description = "一次性添加多个敏感词，逗号或换行分隔")
    public Result<Void> batchAddKeywords(@RequestBody Map<String, Object> body) {
        String words = (String) body.get("words");
        String category = (String) body.getOrDefault("category", "default");

        if (words == null || words.isBlank()) {
            return Result.fail("敏感词不能为空");
        }

        String[] wordList = words.split("[,，\n\r]+");
        int added = 0;
        for (String word : wordList) {
            String trimmed = word.trim();
            if (!trimmed.isEmpty()) {
                try {
                    jdbcTemplate.update(
                            "INSERT INTO sensitive_word (word, category, created_at) VALUES (?, ?, NOW())",
                            trimmed, category);
                    added++;
                } catch (Exception ignored) {
                    // 重复词跳过
                }
            }
        }
        return Result.successMessage("成功添加 " + added + " 个敏感词");
    }

    // ==================== 自动审核规则 ====================

    @GetMapping("/auto")
    @Operation(summary = "自动审核规则列表")
    public Result<List<Map<String, Object>>> listRules() {
        List<Map<String, Object>> rules = jdbcTemplate.queryForList(
                "SELECT id, rule_key, rule_name, rule_value, description, updated_at FROM audit_rule ORDER BY id");
        return Result.success(rules);
    }

    @PutMapping("/auto/{id}")
    @AdminAudit(action = "update_rule", targetType = "rule", description = "更新审核规则")
    @Operation(summary = "更新审核规则")
    public Result<Void> updateRule(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        String ruleValue = (String) body.get("ruleValue");
        if (ruleValue == null) {
            return Result.fail("规则值不能为空");
        }

        int rows = jdbcTemplate.update(
                "UPDATE audit_rule SET rule_value = ?, updated_at = NOW() WHERE id = ?",
                ruleValue, id);
        if (rows == 0) {
            return Result.fail("规则不存在");
        }
        return Result.successMessage("更新成功");
    }
}
