package com.interstellar.post.domain.post;

import com.interstellar.common.Result;
import com.interstellar.common.cache.CacheKeys;
import com.interstellar.common.cache.CacheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/banners")
@Tag(name = "Banner", description = "用户端Banner展示")
public class BannerController {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private CacheService cacheService;

    private static final String CACHE_KEY = "interstellar:banners:";

    @GetMapping
    @Operation(summary = "获取Banner列表", description = "按位置获取当前有效的Banner")
    public Result<List<Map<String, Object>>> list(
            @RequestParam(value = "position", defaultValue = "home_top") String position) {

        String cacheKey = CACHE_KEY + position;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cached = cacheService.get(cacheKey);
        if (cached != null) return Result.success(cached);

        List<Map<String, Object>> banners = jdbcTemplate.queryForList(
                "SELECT id, title, image_url, link_type, link_value, position, sort_order " +
                "FROM banner WHERE status = 1 AND position = ? " +
                "AND (start_time IS NULL OR start_time <= NOW()) " +
                "AND (end_time IS NULL OR end_time >= NOW()) " +
                "ORDER BY sort_order DESC, created_at DESC",
                position);

        cacheService.set(cacheKey, banners, java.time.Duration.ofMinutes(5));
        return Result.success(banners);
    }
}