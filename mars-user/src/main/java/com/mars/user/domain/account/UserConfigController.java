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

    private static final java.util.Set<String> VALID_THEMES = java.util.Set.of("dark", "light");
    private static final java.util.regex.Pattern ACCENT_PATTERN =
            java.util.regex.Pattern.compile("^[0-9a-fA-F]{8}$");

    @Autowired
    private UserProfileMapper profileMapper;

    @GetMapping
    @Operation(summary = "获取用户配置")
    public Result<Map<String, Object>> getConfig(@RequestHeader("X-User-Id") String userIdStr) {
        Long userId = Long.parseLong(userIdStr);
        UserProfile profile = profileMapper.selectById(userId);

        Map<String, Object> config = new HashMap<>();
        config.put("theme", profile != null && profile.getTheme() != null ? profile.getTheme() : "dark");
        config.put("themePreset", profile != null && profile.getThemePreset() != null ? profile.getThemePreset() : "default");
        if (profile != null && profile.getThemeAccent() != null) {
            config.put("themeAccent", profile.getThemeAccent());
        }
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

        boolean dirty = false;

        if (body.containsKey("theme")) {
            String theme = (String) body.get("theme");
            if (!VALID_THEMES.contains(theme)) {
                return Result.fail("主题值无效，仅支持 dark/light");
            }
            profile.setTheme(theme);
            dirty = true;
        }

        if (body.containsKey("themePreset")) {
            String preset = (String) body.get("themePreset");
            if (preset != null && preset.length() > 32) {
                return Result.fail("themePreset 过长");
            }
            profile.setThemePreset(preset);
            dirty = true;
        }

        if (body.containsKey("themeAccent")) {
            String accent = (String) body.get("themeAccent");
            if (accent != null && !ACCENT_PATTERN.matcher(accent).matches()) {
                return Result.fail("themeAccent 格式无效，需为 8 位 hex（ARGB）");
            }
            profile.setThemeAccent(accent);
            dirty = true;
        }

        if (dirty) {
            profileMapper.updateById(profile);
        }

        return Result.successMessage("配置已更新");
    }
}
