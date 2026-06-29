package com.interstellar.user.domain.reputation;

import com.interstellar.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/reputation")
@Tag(name = "声望系统", description = "用户声望（正向累积）")
public class ReputationController {

    @Autowired private ReputationService reputationService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @GetMapping("/{userId}")
    @Operation(summary = "查看声望")
    public Result<Map<String, Object>> getReputation(@PathVariable("userId") Long userId) {
        return Result.success(reputationService.getReputation(userId));
    }

    @GetMapping("/{userId}/logs")
    @Operation(summary = "声望变动记录")
    public Result<List<Map<String, Object>>> getReputationLogs(
            @PathVariable("userId") Long userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        int offset = (page - 1) * size;
        List<Map<String, Object>> logs = jdbcTemplate.queryForList(
                "SELECT rep_change, source_type, source_id, description, create_time " +
                "FROM user_reputation_log WHERE user_id = ? ORDER BY create_time DESC LIMIT ? OFFSET ?",
                userId, size, offset);
        return Result.success(logs);
    }
}
