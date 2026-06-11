package com.mars.post.domain.post;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mars.post.domain.post.Post;
import com.mars.post.domain.post.PostBrowseHistory;
import com.mars.post.domain.post.PostFavorite;
import com.mars.post.domain.post.PostBrowseHistoryMapper;
import com.mars.post.domain.post.PostFavoriteMapper;
import com.mars.post.domain.post.PostMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class PostRecordService {

    private static final int HISTORY_LIMIT = 50;

    @Autowired
    private PostFavoriteMapper postFavoriteMapper;

    @Autowired
    private PostBrowseHistoryMapper postBrowseHistoryMapper;

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private PostQueryService postQueryService;

    public List<Map<String, Object>> listFavorites(Long userId) {
        List<PostFavorite> favorites = postFavoriteMapper.selectList(new LambdaQueryWrapper<PostFavorite>()
                .eq(PostFavorite::getUserId, userId)
                .orderByDesc(PostFavorite::getCreatedAt)
                .orderByDesc(PostFavorite::getId));
        if (favorites.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> postIds = favorites.stream()
                .map(PostFavorite::getPostId)
                .filter(Objects::nonNull)
                .toList();
        List<Post> posts = postQueryService.loadPostsInOrder(postIds, userId);
        return postQueryService.toRecordItems(posts);
    }

    @Transactional
    public boolean toggleFavorite(Long userId, Long postId) {
        ensurePostExists(postId);

        PostFavorite existing = postFavoriteMapper.selectOne(new LambdaQueryWrapper<PostFavorite>()
                .eq(PostFavorite::getUserId, userId)
                .eq(PostFavorite::getPostId, postId)
                .last("limit 1"));
        if (existing != null) {
            postFavoriteMapper.deleteById(existing.getId());
            return false;
        }

        PostFavorite favorite = new PostFavorite();
        favorite.setUserId(userId);
        favorite.setPostId(postId);
        favorite.setCreatedAt(LocalDateTime.now());
        postFavoriteMapper.insert(favorite);
        return true;
    }

    @Transactional
    public void removeFavorite(Long userId, Long postId) {
        postFavoriteMapper.delete(new LambdaQueryWrapper<PostFavorite>()
                .eq(PostFavorite::getUserId, userId)
                .eq(PostFavorite::getPostId, postId));
    }

    @Transactional
    public void clearFavorites(Long userId) {
        postFavoriteMapper.delete(new LambdaQueryWrapper<PostFavorite>()
                .eq(PostFavorite::getUserId, userId));
    }

    @Transactional
    public void recordHistory(Long userId, Long postId) {
        ensurePostExists(postId);

        LocalDateTime now = LocalDateTime.now();
        PostBrowseHistory existing = postBrowseHistoryMapper.selectOne(new LambdaQueryWrapper<PostBrowseHistory>()
                .eq(PostBrowseHistory::getUserId, userId)
                .eq(PostBrowseHistory::getPostId, postId)
                .last("limit 1"));

        if (existing == null) {
            PostBrowseHistory history = new PostBrowseHistory();
            history.setUserId(userId);
            history.setPostId(postId);
            history.setViewCount(1);
            history.setLastViewedAt(now);
            history.setCreatedAt(now);
            history.setUpdatedAt(now);
            postBrowseHistoryMapper.insert(history);
        } else {
            int currentViewCount = existing.getViewCount() == null ? 0 : existing.getViewCount();
            existing.setViewCount(currentViewCount + 1);
            existing.setLastViewedAt(now);
            existing.setUpdatedAt(now);
            postBrowseHistoryMapper.updateById(existing);
        }

        trimHistory(userId);
    }

    public List<Map<String, Object>> listHistory(Long userId) {
        List<PostBrowseHistory> historyList = postBrowseHistoryMapper.selectList(new LambdaQueryWrapper<PostBrowseHistory>()
                .eq(PostBrowseHistory::getUserId, userId)
                .orderByDesc(PostBrowseHistory::getLastViewedAt)
                .orderByDesc(PostBrowseHistory::getId)
                .last("limit " + HISTORY_LIMIT));
        if (historyList.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> postIds = historyList.stream()
                .map(PostBrowseHistory::getPostId)
                .filter(Objects::nonNull)
                .toList();
        List<Post> posts = postQueryService.loadPostsInOrder(postIds, userId);
        return postQueryService.toRecordItems(posts);
    }

    @Transactional
    public void removeHistory(Long userId, Long postId) {
        postBrowseHistoryMapper.delete(new LambdaQueryWrapper<PostBrowseHistory>()
                .eq(PostBrowseHistory::getUserId, userId)
                .eq(PostBrowseHistory::getPostId, postId));
    }

    @Transactional
    public void clearHistory(Long userId) {
        postBrowseHistoryMapper.delete(new LambdaQueryWrapper<PostBrowseHistory>()
                .eq(PostBrowseHistory::getUserId, userId));
    }

    private void ensurePostExists(Long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new IllegalArgumentException("Post not found");
        }
    }

    private void trimHistory(Long userId) {
        List<PostBrowseHistory> allHistory = postBrowseHistoryMapper.selectList(new LambdaQueryWrapper<PostBrowseHistory>()
                .eq(PostBrowseHistory::getUserId, userId)
                .orderByDesc(PostBrowseHistory::getLastViewedAt)
                .orderByDesc(PostBrowseHistory::getId));
        if (allHistory.size() <= HISTORY_LIMIT) {
            return;
        }

        List<Long> staleIds = allHistory.subList(HISTORY_LIMIT, allHistory.size()).stream()
                .map(PostBrowseHistory::getId)
                .filter(Objects::nonNull)
                .toList();
        if (!staleIds.isEmpty()) {
            postBrowseHistoryMapper.delete(new LambdaQueryWrapper<PostBrowseHistory>().in(PostBrowseHistory::getId, staleIds));
        }
    }
}
