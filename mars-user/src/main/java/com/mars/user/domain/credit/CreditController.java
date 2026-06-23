package com.mars.user.domain.credit;

import com.mars.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/credit")
@Tag(name = "信用分", description = "用户信用分与违规记录")
class CreditController {

    @Autowired private CreditService creditService;
    @Autowired private JdbcTemplate jdbcTemplate;

    /**
     * 查看自己的信用分
     */
    @GetMapping("/{userId}")
    @Operation(summary = "查看信用分")
    public Result<Map<String, Object>> getCreditInfo(@PathVariable("userId") Long userId) {
        Integer creditScore = jdbcTemplate.queryForObject(
                "SELECT credit_score FROM user_profile WHERE user_id = ?", Integer.class, userId);
        int score = creditScore != null ? creditScore : 100;

        Map<String, Object> result = new HashMap<>();
        result.put("creditScore", score);
        result.put("level", creditService.getCreditLevel(score));
        result.put("restriction", creditService.getRestriction(score));
        return Result.success(result);
    }

    /**
     * 违规记录列表
     */
    @GetMapping("/{userId}/violations")
    @Operation(summary = "违规记录")
    public Result<List<Map<String, Object>>> getViolations(
            @PathVariable("userId") Long userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        int offset = (page - 1) * size;
        List<Map<String, Object>> violations = jdbcTemplate.queryForList(
                "SELECT id, violation_type, severity, score_deducted, reason, create_time " +
                "FROM user_violation WHERE user_id = ? ORDER BY create_time DESC LIMIT ? OFFSET ?",
                userId, size, offset);
        return Result.success(violations);
    }
}