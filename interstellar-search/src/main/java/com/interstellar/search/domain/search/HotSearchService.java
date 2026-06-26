package com.interstellar.search.domain.search;

import com.interstellar.common.Result;
import com.interstellar.common.cache.CacheKeys;
import com.interstellar.common.cache.CacheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 热搜服务：基于 Redis ZSet 的热度计算
 * 热度公式：like_count * 1 + comment_count * 2 + share_count * 3，结合时间衰减
 */
@Service
class HotSearchService {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private RedisTemplate<String, Object> redisTemplate;

    private static final String HOT_POSTS_KEY = "interstellar:hot:posts";
    private static final String HOT_SEARCH_KEY = "interstellar:hot:search";
    private static final int HOT_POSTS_LIMIT = 50;
    private static final int HOT_SEARCH_LIMIT = 20;

    /**
     * 每 5 分钟刷新热帖榜单
     */
    @Scheduled(fixedRate = 300000)
    public void refreshHotPosts() {
        try {
            List<Map<String, Object>> posts = jdbcTemplate.queryForList(
                    "SELECT id, like_count, comment_count, share_count, create_time, title, username " +
                    "FROM post WHERE display_status = 1 AND create_time >= DATE_SUB(NOW(), INTERVAL 7 DAY) " +
                    "ORDER BY (like_count * 1 + comment_count * 2 + share_count * 3) DESC, create_time DESC " +
                    "LIMIT " + HOT_POSTS_LIMIT);

            redisTemplate.delete(HOT_POSTS_KEY);

            for (Map<String, Object> post : posts) {
                Long id = ((Number) post.get("id")).longValue();
                int likes = ((Number) post.get("like_count")).intValue();
                int comments = ((Number) post.get("comment_count")).intValue();
                int shares = ((Number) post.get("share_count")).intValue();

                LocalDateTime createTime = (LocalDateTime) post.get("create_time");
                long hoursSince = java.time.Duration.between(createTime, LocalDateTime.now()).toHours();
                double timeDecay = 1.0 / (1.0 + hoursSince / 24.0);
                double heatScore = (likes * 1.0 + comments * 2.0 + shares * 3.0) * timeDecay;

                redisTemplate.opsForZSet().add(HOT_POSTS_KEY, id, heatScore);
            }

            refreshHotSearchKeywords();
        } catch (Exception e) {
            // 静默失败
        }
    }

    private void refreshHotSearchKeywords() {
        try {
            List<Map<String, Object>> keywords = jdbcTemplate.queryForList(
                    "SELECT keyword, heat_score FROM hot_search WHERE status = 1 ORDER BY heat_score DESC, `rank` ASC LIMIT " + HOT_SEARCH_LIMIT);

            redisTemplate.delete(HOT_SEARCH_KEY);
            for (Map<String, Object> kw : keywords) {
                String keyword = (String) kw.get("keyword");
                Long score = ((Number) kw.get("heat_score")).longValue();
                redisTemplate.opsForZSet().add(HOT_SEARCH_KEY, keyword, score);
            }
        } catch (Exception ignored) {}
    }

    public Set<Object> getHotPosts(int limit) {
        Set<Object> ids = redisTemplate.opsForZSet().reverseRange(HOT_POSTS_KEY, 0, limit - 1);
        return ids != null ? ids : Collections.emptySet();
    }

    public Set<Object> getHotSearchKeywords(int limit) {
        Set<Object> keywords = redisTemplate.opsForZSet().reverseRange(HOT_SEARCH_KEY, 0, limit - 1);
        return keywords != null ? keywords : Collections.emptySet();
    }

    public void recordSearchKeyword(String keyword) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO hot_search (keyword, heat_score, source) VALUES (?, 1, 'auto') " +
                    "ON DUPLICATE KEY UPDATE heat_score = heat_score + 1",
                    keyword);
        } catch (Exception ignored) {}
    }
}

@RestController
@RequestMapping("/search")
@Tag(name = "热搜", description = "热搜词和热帖榜单")
class HotSearchController {

    @Autowired private HotSearchService hotSearchService;
    @Autowired private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @GetMapping("/hot-posts")
    @Operation(summary = "热帖榜单")
    public Result<List<Map<String, Object>>> hotPosts(
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        Set<Object> ids = hotSearchService.getHotPosts(limit);
        if (ids.isEmpty()) return Result.success(Collections.emptyList());

        List<Long> idList = ids.stream()
                .map(id -> ((Number) id).longValue())
                .collect(Collectors.toList());
        MapSqlParameterSource params = new MapSqlParameterSource("ids", idList);
        List<Map<String, Object>> posts = namedParameterJdbcTemplate.queryForList(
                "SELECT id, user_id, username, title, summary, like_count, comment_count, share_count, create_time " +
                "FROM post WHERE id IN (:ids) " +
                "ORDER BY (like_count * 1 + comment_count * 2 + share_count * 3) DESC", params);
        return Result.success(posts);
    }
}

@RestController
@RequestMapping("/admin/hot-search")
@Tag(name = "热搜管理", description = "管理员热搜词管理")
class AdminHotSearchController {

    @Autowired private JdbcTemplate jdbcTemplate;

    @GetMapping
    @Operation(summary = "热搜词列表")
    public Result<List<Map<String, Object>>> list(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        int offset = (page - 1) * size;
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
                "SELECT * FROM hot_search ORDER BY `rank` ASC, heat_score DESC LIMIT ? OFFSET ?", size, offset);
        return Result.success(records);
    }

    @PostMapping
    @Operation(summary = "添加热搜词（人工置顶）")
    public Result<String> add(@RequestBody Map<String, Object> body) {
        String keyword = (String) body.get("keyword");
        Integer rank = (Integer) body.getOrDefault("rank", 0);
        if (keyword == null || keyword.isBlank()) return Result.fail("关键词不能为空");

        try {
            jdbcTemplate.update(
                    "INSERT INTO hot_search (keyword, heat_score, `rank`, source) VALUES (?, 0, ?, 'manual') " +
                    "ON DUPLICATE KEY UPDATE `rank` = ?, source = 'manual'",
                    keyword, rank, rank);
        } catch (Exception e) {
            return Result.fail("添加失败");
        }
        return Result.successMessage("添加成功");
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "上下线热搜词")
    public Result<String> toggleStatus(@PathVariable("id") Long id) {
        List<Map<String, Object>> records = jdbcTemplate.queryForList("SELECT status FROM hot_search WHERE id = ?", id);
        if (records.isEmpty()) return Result.fail("热搜词不存在");

        Integer current = (Integer) records.get(0).get("status");
        int newStatus = (current != null && current == 1) ? 0 : 1;
        jdbcTemplate.update("UPDATE hot_search SET status = ? WHERE id = ?", newStatus, id);
        return Result.successMessage(newStatus == 1 ? "已上线" : "已下线");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除热搜词")
    public Result<String> delete(@PathVariable("id") Long id) {
        jdbcTemplate.update("DELETE FROM hot_search WHERE id = ?", id);
        return Result.successMessage("删除成功");
    }
}
