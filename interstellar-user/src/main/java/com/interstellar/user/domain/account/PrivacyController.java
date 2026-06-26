package com.interstellar.user.domain.account;

import com.interstellar.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/user/profile")
@Tag(name = "隐私设置", description = "用户主页隐私设置")
public class PrivacyController {

    @Autowired private JdbcTemplate jdbcTemplate;

    @PutMapping("/privacy")
    @Operation(summary = "更新隐私设置")
    public Result<String> updatePrivacy(
            @RequestHeader("X-User-Id") String userIdStr,
            @RequestBody Map<String, Object> body) {

        Long userId = Long.parseLong(userIdStr);
        StringBuilder sql = new StringBuilder("UPDATE user_profile SET ");
        java.util.List<Object> params = new java.util.ArrayList<>();

        if (body.containsKey("profileVisibility")) {
            sql.append("profile_visibility = ?, ");
            params.add(body.get("profileVisibility"));
        }
        if (body.containsKey("followingVisible")) {
            sql.append("following_visible = ?, ");
            params.add(body.get("followingVisible"));
        }
        if (body.containsKey("followerVisible")) {
            sql.append("follower_visible = ?, ");
            params.add(body.get("followerVisible"));
        }

        if (params.isEmpty()) return Result.fail("无更新内容");

        sql.append("updated_at = NOW() WHERE user_id = ?");
        params.add(userId);

        jdbcTemplate.update(sql.toString(), params.toArray());
        return Result.successMessage("隐私设置已更新");
    }

    @GetMapping("/privacy")
    @Operation(summary = "获取隐私设置")
    public Result<Map<String, Object>> getPrivacy(@RequestHeader("X-User-Id") String userIdStr) {
        Long userId = Long.parseLong(userIdStr);
        Map<String, Object> result = jdbcTemplate.queryForMap(
                "SELECT profile_visibility, following_visible, follower_visible FROM user_profile WHERE user_id = ?", userId);
        return Result.success(result);
    }
}