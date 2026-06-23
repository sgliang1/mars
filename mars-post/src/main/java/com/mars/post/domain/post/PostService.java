package com.mars.post.domain.post;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mars.post.domain.post.PostDTO;
import com.mars.post.domain.post.Post;
import com.mars.post.domain.post.PostContent;
import com.mars.post.domain.post.PostImage;
import com.mars.post.domain.post.PostLike;
import com.mars.post.domain.comment.CommentMapper;
import com.mars.post.domain.post.PostContentMapper;
import com.mars.post.domain.post.PostImageMapper;
import com.mars.post.domain.post.PostLikeMapper;
import com.mars.post.domain.post.PostMapper;
import com.mars.post.domain.notification.NotificationHelper;
import com.mars.common.cache.CacheKeys;
import com.mars.common.cache.CacheService;
import com.mars.post.mq.SearchSyncProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
public class PostService {
    @Autowired private PostMapper postMapper;
    @Autowired private PostContentMapper contentMapper;
    @Autowired private PostImageMapper postImageMapper;
    @Autowired private PostLikeMapper postLikeMapper;
    @Autowired private CommentMapper commentMapper;
    @Autowired private NotificationHelper notificationHelper;
    @Autowired private CacheService cacheService;
    @Autowired private SearchSyncProducer searchSyncProducer;
    @Autowired private PostEditHistoryMapper editHistoryMapper;
    @Autowired private FeedService feedService;

    @Transactional // ??????????????????
    public void publish(Post post, String fullContent) {
        post.setSummary(buildSummary(fullContent));
        
        // 2. ??????????ID
        postMapper.insert(post); 
        
        // 3. ?? ID ??????
        PostContent pc = new PostContent();
        pc.setPostId(post.getId());
        pc.setContent(fullContent);
        contentMapper.insert(pc);

        // 通知 ES 索引同步
        try { searchSyncProducer.sendPostSync(post.getId(), "INDEX"); } catch (Exception ignored) {}

        // 清除 Feed 缓存
        feedService.evictUserFeedCache(post.getUserId());
        feedService.evictHotFeedCache();
    }

    @Transactional
    public boolean updatePost(Long postId, Long userId, PostDTO postDTO) {
        Post post = postMapper.selectById(postId);
        if (post == null || !post.getUserId().equals(userId)) {
            return false;
        }

        // 保存编辑历史
        PostContent currentContent = contentMapper.selectById(postId);
        if (currentContent != null && currentContent.getContent() != null) {
            PostEditHistory history = new PostEditHistory();
            history.setPostId(postId);
            history.setContentSnapshot(currentContent.getContent());
            history.setEditedAt(java.time.LocalDateTime.now());
            history.setEditorId(userId);
            editHistoryMapper.insert(history);
        }

        String content = postDTO.getContent() == null ? "" : postDTO.getContent();
        // 前端未传 title 时保留原标题，或从正文生成
        String newTitle = postDTO.getTitle();
        if (newTitle == null || newTitle.isBlank()) {
            if (post.getTitle() != null && !post.getTitle().isBlank()) {
                newTitle = post.getTitle();
            } else if (!content.isBlank()) {
                newTitle = content.contains("\n")
                    ? content.substring(0, content.indexOf("\n")).trim()
                    : content.length() > 30 ? content.substring(0, 30) : content;
            } else {
                newTitle = "未命名帖子";
            }
        }
        post.setTitle(newTitle);
        post.setSummary(buildSummary(content));
        post.setEditCount(post.getEditCount() == null ? 1 : post.getEditCount() + 1);
        postMapper.updateById(post);

        PostContent contentEntity = contentMapper.selectById(postId);
        if (contentEntity == null) {
            contentEntity = new PostContent();
            contentEntity.setPostId(postId);
            contentEntity.setContent(content);
            contentMapper.insert(contentEntity);
        } else {
            contentEntity.setContent(content);
            contentMapper.updateById(contentEntity);
        }

        if (postDTO.getImages() != null) {
            postImageMapper.delete(new LambdaQueryWrapper<PostImage>()
                    .eq(PostImage::getPostId, postId));

            List<String> images = splitImages(postDTO.getImages());
            for (int i = 0; i < images.size(); i++) {
                PostImage image = new PostImage();
                image.setPostId(postId);
                image.setUrl(images.get(i));
                image.setSort(i);
                postImageMapper.insert(image);
            }
        }

        // 清除帖子详情缓存
        cacheService.delete(CacheKeys.key(CacheKeys.POST_DETAIL, postId));
        // 通知 ES 重新索引
        try { searchSyncProducer.sendPostSync(postId, "INDEX"); } catch (Exception ignored) {}
        return true;
    }

    @Transactional
    public boolean deletePost(Long postId, Long userId) {
        Post post = postMapper.selectById(postId);
        if (post == null || !post.getUserId().equals(userId)) {
            return false;
        }

        // 软删除：设置 deletedAt，保留数据
        post.setDeletedAt(java.time.LocalDateTime.now());
        postMapper.updateById(post);

        // 清除帖子详情缓存
        cacheService.delete(CacheKeys.key(CacheKeys.POST_DETAIL, postId));
        // 通知 ES 删除文档
        try { searchSyncProducer.sendPostSync(postId, "DELETE"); } catch (Exception ignored) {}
        // 清除 Feed 缓存
        feedService.evictUserFeedCache(post.getUserId());
        feedService.evictHotFeedCache();
        return true;
    }

    /**
     * ??/???? ? ?? + ?????
     * @return "liked" ???????"unliked" ??????
     */
    @Transactional
    public String toggleLike(Long postId, Long userId, String username) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new IllegalArgumentException("?????");
        }

        PostLike existingLike = postLikeMapper.selectOne(new LambdaQueryWrapper<PostLike>()
                .eq(PostLike::getPostId, postId)
                .eq(PostLike::getUserId, userId));

        if (existingLike != null) {
            postLikeMapper.deleteById(existingLike.getId());
            postMapper.decrementLikeCount(postId);
            // 清除帖子详情缓存
            cacheService.delete(CacheKeys.key(CacheKeys.POST_DETAIL, postId));
            // 清除 Feed 缓存
            feedService.evictHotFeedCache();
            return "unliked";
        } else {
            PostLike like = new PostLike();
            like.setPostId(postId);
            like.setUserId(userId);
            like.setCreateTime(java.time.LocalDateTime.now());
            postLikeMapper.insert(like);
            postMapper.incrementLikeCount(postId);

            // 清除帖子详情缓存
            cacheService.delete(CacheKeys.key(CacheKeys.POST_DETAIL, postId));
            // 清除 Feed 缓存
            feedService.evictHotFeedCache();
            try {
                notificationHelper.notifyLike(userId, username != null ? username : "????", postId);
            } catch (Exception ignored) {}

            return "liked";
        }
    }

    private String buildSummary(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        return content.length() > 140 ? content.substring(0, 140) + "..." : content;
    }

    private List<String> splitImages(String images) {
        if (images == null || images.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(images.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }
}
