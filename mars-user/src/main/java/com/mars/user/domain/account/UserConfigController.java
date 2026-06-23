package com.mars.user.domain.account;

import com.mars.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/user/config")
@Tag(name = "用户配置", description = "用户偏好配置（主题等）")
public class UserConfigController {

    @Autowired
    private UserProfileMapper profileMapper;

    @GetMapping
    @Operation(summary = "获取用户配置")
    public Result<Map<String, Object>> getConfig(@RequestHeader("X-User-Id") String userIdStr) {
        Long userId = Long.parseLong(userIdStr);
        UserProfile profile = profileMapper.selectById(userId);

        Map<String, Object> config = new HashMap<>();
        config.put("theme", profile != null && profile.getTheme() != null ? profile.getTheme() : "dark");
        return Result.success(config);
    }

    @PutMapping
    @Operation(summary = "更新用户配置")
    public Result<String> updateConfig(@RequestHeader("X-User-Id") String userIdStr,
                                       @RequestBody Map<String, Object> body) {
        Long userId = Long.parseLong(userIdStr);
        UserProfile profile = profileMapper.selectById(userId);

        if (profile == null) {
            return Result.fail("用户资料不存在");
        }

        if (body.containsKey("theme")) {
            String theme = (String) body.get("theme");
            if (!"dark".equals(theme) && !"light".equals(theme)) {
                return Result.fail("主题值无效，仅支持 dark/light");
            }
            profile.setTheme(theme);
            profileMapper.updateById(profile);
        }

        return Result.successMessage("配置已更新");
    }
}