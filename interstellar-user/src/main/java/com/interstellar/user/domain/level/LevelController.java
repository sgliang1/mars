package com.interstellar.user.domain.level;

import com.interstellar.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/level")
@Tag(name = "等级体系", description = "用户等级与经验值")
class LevelController {

    @Autowired private LevelService levelService;
    @Autowired private JdbcTemplate jdbcTemplate;

    /**
     * 查看自己的等级信息
     */
    @GetMapping("/{userId}")
    @Operation(summary = "查看等级信息")
    public Result<Map<String, Object>> getLevelInfo(@PathVariable("userId") Long userId) {
        Map<String, Object> profile = jdbcTemplate.queryForMap(
                "SELECT level, exp_points, level_name FROM user_profile WHERE user_id = ?", userId);

        Map<String, Object> result = new HashMap<>();
        result.put("level", profile.get("level"));
        result.put("expPoints", profile.get("exp_points"));
        result.put("levelName", profile.get("level_name"));
        result.put("nextLevelExp", levelService.getNextLevelExp(((Number) profile.get("level")).intValue()));
        return Result.success(result);
    }

    /**
     * 经验值变动记录
     */
    @GetMapping("/{userId}/logs")
    @Operation(summary = "经验值变动记录", description = "分页查询经验值获取/扣除记录")
    public Result<List<Map<String, Object>>> getExpLogs(
            @PathVariable("userId") Long userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        int offset = (page - 1) * size;
        List<Map<String, Object>> logs = jdbcTemplate.queryForList(
                "SELECT exp_change, source_type, source_id, description, create_time " +
                "FROM user_exp_log WHERE user_id = ? ORDER BY create_time DESC LIMIT ? OFFSET ?",
                userId, size, offset);
        return Result.success(logs);
    }

    /**
     * 等级规则配置（前端展示用）
     */
    @GetMapping("/rules")
    @Operation(summary = "等级规则", description = "返回等级名称、所需经验值、权益")
    public Result<List<Map<String, Object>>> getLevelRules() {
        List<Map<String, Object>> rules = java.util.Arrays.asList(
                Map.of("level", 1, "name", "新手", "expRequired", 0, "icon", "🌱"),
                Map.of("level", 2, "name", "初级", "expRequired", 100, "icon", "⭐"),
                Map.of("level", 3, "name", "中级", "expRequired", 500, "icon", "🔥"),
                Map.of("level", 4, "name", "高级", "expRequired", 1500, "icon", "💎"),
                Map.of("level", 5, "name", "资深", "expRequired", 5000, "icon", "👑"),
                Map.of("level", 6, "name", "大师", "expRequired", 15000, "icon", "🏆")
        );
        return Result.success(rules);
    }
}