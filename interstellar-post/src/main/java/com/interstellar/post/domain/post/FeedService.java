package com.interstellar.post.domain.post;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.interstellar.common.Result;
import com.interstellar.common.cache.CacheKeys;
import com.interstellar.common.cache.CacheService;
import com.interstellar.post.domain.filter.UserFilterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FeedService {

    @Autowired private PostMapper postMapper;
    @Autowired private PostImageMapper postImageMapper;
    @Autowired private PostLikeMapper postLikeMapper;
    @Autowired private NamedParameterJdbcTemplate namedJdbcTemplate;
    @Autowired private CacheService cacheService;
    @Autowired private RedisTemplate<String, Object> redisTemplate;

    private static final String FEED_CACHE_KEY = "interstellar:feed:";
    private static final Duration FEED_CACHE_TTL = Duration.ofMinutes(5);

    /**
     * 关注流：已关注用户的帖子，按时间倒序
     */
    public List<Post> getFollowingFeed(Long userId, int page, int size) {
        String cacheKey = FEED_CACHE_KEY + "following:" + userId + ":" + page;
        @SuppressWarnings("unchecked")
        List<Post> cached = cacheService.get(cacheKey);
        if (cached != null) return cached;

        List<Long> followingIds = namedJdbcTemplate.getJdbcTemplate().queryForList(
                "SELECT followed_id FROM user_relation WHERE follower_id = ?", Long.class, userId);

        if (followingIds.isEmpty()) {
            return Collections.emptyList();
        }

        int offset = (page - 1) * size;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("followingIds", followingIds)
                .addValue("size", size)
                .addValue("offset", offset);

        List<Map<String, Object>> rows = namedJdbcTemplate.queryForList(
                "SELECT * FROM post WHERE user_id IN (:followingIds) AND display_status = 1 " +
                "AND visibility IN (0, 1) AND deleted_at IS NULL " +
                "ORDER BY is_pinned DESC, pinned_at DESC, create_time DESC " +
                "LIMIT :size OFFSET :offset", params);

        List<Post> posts = mapToPosts(rows);
        cacheService.set(cacheKey, posts, FEED_CACHE_TTL);
        return posts;
    }

    /**
     * 热度流：全站热度最高的帖子（带时间衰减，每过一天热度减半）
     */
    public List<Post> getHotFeed(int page, int size) {
        String cacheKey = FEED_CACHE_KEY + "hot:" + page;
        @SuppressWarnings("unchecked")
        List<Post> cached = cacheService.get(cacheKey);
        if (cached != null) return cached;

        int offset = (page - 1) * size;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("size", size)
                .addValue("offset", offset);

        List<Map<String, Object>> rows = namedJdbcTemplate.queryForList(
                "SELECT * FROM post WHERE display_status = 1 AND visibility = 0 AND deleted_at IS NULL " +
                "ORDER BY ((like_count * 1 + comment_count * 2 + share_count * 3) " +
                "/ POW(2, TIMESTAMPDIFF(HOUR, create_time, NOW()) / 24.0)) DESC, create_time DESC " +
                "LIMIT :size OFFSET :offset", params);

        List<Post> posts = mapToPosts(rows);
        cacheService.set(cacheKey, posts, FEED_CACHE_TTL);
        return posts;
    }

    /**
     * 最新流：纯时间倒序
     */
    public List<Post> getLatestFeed(int page, int size) {
        String cacheKey = FEED_CACHE_KEY + "latest:" + page;
        @SuppressWarnings("unchecked")
        List<Post> cached = cacheService.get(cacheKey);
        if (cached != null) return cached;

        int offset = (page - 1) * size;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("size", size)
                .addValue("offset", offset);

        List<Map<String, Object>> rows = namedJdbcTemplate.queryForList(
                "SELECT * FROM post WHERE display_status = 1 AND visibility = 0 AND deleted_at IS NULL " +
                "ORDER BY create_time DESC " +
                "LIMIT :size OFFSET :offset", params);

        List<Post> posts = mapToPosts(rows);
        cacheService.set(cacheKey, posts, FEED_CACHE_TTL);
        return posts;
    }

    /**
     * 推荐流 V2：70% 关注流 + 30% 个性化热度流
     */
    public List<Post> getRecommendedFeed(Long userId, int page, int size) {
        String cacheKey = FEED_CACHE_KEY + "recommended:" + userId + ":" + page;
        @SuppressWarnings("unchecked")
        List<Post> cached = cacheService.get(cacheKey);
        if (cached != null) return cached;

        int followingSize = (int) Math.ceil(size * 0.7);
        int hotSize = size - followingSize;

        List<Post> following = getFollowingFeed(userId, page, followingSize);
        List<Post> hot = getPersonalizedHotFeed(userId, hotSize);

        List<Post> result = new ArrayList<>();
        int fi = 0, hi = 0;
        while (result.size() < size && (fi < following.size() || hi < hot.size())) {
            if (fi < following.size()) result.add(following.get(fi++));
            if (hi < hot.size() && result.size() < size) result.add(hot.get(hi++));
        }

        Set<Long> seen = new HashSet<>();
        result = result.stream().filter(p -> seen.add(p.getId())).collect(Collectors.toList());

        cacheService.set(cacheKey, result, FEED_CACHE_TTL);
        return result;
    }

    /**
     * 个性化热度流
     */
    private List<Post> getPersonalizedHotFeed(Long userId, int size) {
        List<Long> interactedAuthorIds = namedJdbcTemplate.getJdbcTemplate().queryForList(
                "SELECT p.user_id FROM user_behavior ub " +
                "JOIN post p ON ub.target_id = p.id " +
                "WHERE ub.user_id = ? AND ub.create_time > DATE_SUB(NOW(), INTERVAL 7 DAY) " +
                "GROUP BY p.user_id ORDER BY MAX(ub.create_time) DESC LIMIT 20",
                Long.class, userId);

        if (interactedAuthorIds.isEmpty()) {
            return getHotFeed(1, size);
        }

        int personalizedSize = (int) Math.ceil(size * 0.6);
        int globalSize = size - personalizedSize;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("authorIds", interactedAuthorIds)
                .addValue("limit", personalizedSize);

        List<Map<String, Object>> personalizedRows = namedJdbcTemplate.queryForList(
                "SELECT * FROM post WHERE user_id IN (:authorIds) " +
                "AND display_status = 1 AND visibility = 0 AND deleted_at IS NULL " +
                "ORDER BY ((like_count * 1 + comment_count * 2 + share_count * 3) " +
                "/ POW(2, TIMESTAMPDIFF(HOUR, create_time, NOW()) / 24.0)) DESC " +
                "LIMIT :limit", params);

        List<Post> personalized = mapToPosts(personalizedRows);
        List<Post> global = getHotFeed(1, globalSize);

        Set<Long> seen = personalized.stream().map(Post::getId).collect(Collectors.toSet());
        for (Post p : global) {
            if (seen.add(p.getId()) && personalized.size() < size) {
                personalized.add(p);
            }
        }
        return personalized;
    }

    // ==================== 缓存失效 ====================

    /**
     * 清除用户相关的 Feed 缓存（发帖、关注变更时调用）
     */
    public void evictUserFeedCache(Long userId) {
        Set<String> keys = cacheService.scanKeys(FEED_CACHE_KEY + "following:" + userId + ":*");
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        Set<String> recKeys = cacheService.scanKeys(FEED_CACHE_KEY + "recommended:" + userId + ":*");
        if (!recKeys.isEmpty()) {
            redisTemplate.delete(recKeys);
        }
    }

    /**
     * 清除全站热度/最新流缓存（点赞、评论、转发时调用）
     */
    public void evictHotFeedCache() {
        Set<String> hotKeys = cacheService.scanKeys(FEED_CACHE_KEY + "hot:*");
        if (!hotKeys.isEmpty()) {
            redisTemplate.delete(hotKeys);
        }
        Set<String> latestKeys = cacheService.scanKeys(FEED_CACHE_KEY + "latest:*");
        if (!latestKeys.isEmpty()) {
            redisTemplate.delete(latestKeys);
        }
    }

    /**
     * 记录用户行为
     */
    public void recordBehavior(Long userId, Long targetId, String action) {
        try {
            namedJdbcTemplate.getJdbcTemplate().update(
                    "INSERT INTO user_behavior (user_id, target_id, action) VALUES (?, ?, ?)",
                    userId, targetId, action);
        } catch (Exception e) {
            log.warn("记录用户行为失败: userId={}, targetId={}", userId, targetId, e);
        }
    }

    private List<Post> mapToPosts(List<Map<String, Object>> rows) {
        return rows.stream().map(row -> {
            Post post = new Post();
            post.setId(((Number) row.get("id")).longValue());
            post.setUserId(((Number) row.get("user_id")).longValue());
            post.setUsername((String) row.get("username"));
            post.setTitle((String) row.get("title"));
            post.setSummary((String) row.get("summary"));
            post.setLikeCount(((Number) row.get("like_count")).intValue());
            post.setCommentCount(((Number) row.get("comment_count")).intValue());
            post.setShareCount(((Number) row.get("share_count")).intValue());
            post.setIsPinned(((Number) row.get("is_pinned")).intValue());
            post.setIsFeatured(((Number) row.get("is_featured")).intValue());
            post.setVisibility(((Number) row.get("visibility")).intValue());
            post.setCreateTime((LocalDateTime) row.get("create_time"));
            return post;
        }).collect(Collectors.toList());
    }
}

@RestController
@RequestMapping("/posts/feed")
@Tag(name = "信息流", description = "个性化推荐Feed")
class FeedController {

    @Autowired private FeedService feedService;
    @Autowired private PostImageMapper postImageMapper;
    @Autowired private PostLikeMapper postLikeMapper;
    @Autowired private CacheService cacheService;
    @Autowired private com.interstellar.post.domain.filter.UserFilterService userFilterService;

    @GetMapping
    @Operation(summary = "获取Feed流", description = "type: following(关注)/recommended(推荐)/hot(热门)")
    public Result<List<Post>> feed(
            @RequestHeader("X-User-Id") String userIdStr,
            @RequestParam(value = "type", defaultValue = "recommended") String type,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {

        Long userId = Long.parseLong(userIdStr);
        List<Post> posts;

        switch (type) {
            case "following" -> posts = feedService.getFollowingFeed(userId, page, size);
            case "hot" -> posts = feedService.getHotFeed(page, size);
            case "latest" -> posts = feedService.getLatestFeed(page, size);
            default -> posts = feedService.getRecommendedFeed(userId, page, size);
        }

        if (posts.isEmpty()) return Result.success(posts);

        Set<Long> filteredIds = userFilterService.getFilteredIds(userId);
        if (!filteredIds.isEmpty()) {
            posts = posts.stream().filter(p -> !filteredIds.contains(p.getUserId())).collect(Collectors.toList());
            if (posts.isEmpty()) return Result.success(posts);
        }

        List<Long> postIds = posts.stream().map(Post::getId).collect(Collectors.toList());
        Map<Long, List<String>> imageMap = new HashMap<>();
        postImageMapper.selectList(new LambdaQueryWrapper<PostImage>()
                .in(PostImage::getPostId, postIds)
                .orderByAsc(PostImage::getSort))
                .forEach(img -> imageMap.computeIfAbsent(img.getPostId(), k -> new ArrayList<>()).add(img.getUrl()));
        posts.forEach(p -> p.setImageList(imageMap.getOrDefault(p.getId(), new ArrayList<>())));
        Set<Long> likedIds = postLikeMapper.selectList(new LambdaQueryWrapper<PostLike>()
                .eq(PostLike::getUserId, userId).in(PostLike::getPostId, postIds))
                .stream().map(PostLike::getPostId).collect(Collectors.toSet());
        posts.forEach(p -> p.setLiked(likedIds.contains(p.getId())));

        return Result.success(posts);
    }
}