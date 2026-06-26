package com.interstellar.post.domain.post;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interstellar.post.domain.post.Post;
import com.interstellar.post.domain.post.PostImage;
import com.interstellar.post.domain.post.PostLike;
import com.interstellar.post.domain.post.PostImageMapper;
import com.interstellar.post.domain.post.PostLikeMapper;
import com.interstellar.post.domain.post.PostMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PostQueryService {

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private PostImageMapper postImageMapper;

    @Autowired
    private PostLikeMapper postLikeMapper;

    public List<Post> loadPostsInOrder(List<Long> orderedPostIds, Long userId) {
        if (orderedPostIds == null || orderedPostIds.isEmpty()) {
            return Collections.emptyList();
        }

        Collection<Long> uniqueIds = new LinkedHashSet<>(orderedPostIds);
        Map<Long, Post> postMap = postMapper.selectBatchIds(uniqueIds).stream()
                .collect(Collectors.toMap(Post::getId, post -> post, (left, right) -> left));

        List<Post> orderedPosts = new ArrayList<>();
        for (Long postId : orderedPostIds) {
            Post post = postMap.get(postId);
            if (post != null) {
                orderedPosts.add(post);
            }
        }
        return attachPostExtras(orderedPosts, userId);
    }

    public List<Post> attachPostExtras(List<Post> postList, Long userId) {
        if (postList == null || postList.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> postIds = postList.stream()
                .map(Post::getId)
                .filter(Objects::nonNull)
                .toList();
        if (postIds.isEmpty()) {
            return postList;
        }

        List<PostImage> allImages = postImageMapper.selectList(new LambdaQueryWrapper<PostImage>()
                .in(PostImage::getPostId, postIds)
                .orderByAsc(PostImage::getSort));

        Map<Long, List<String>> imageMap = new HashMap<>();
        for (PostImage image : allImages) {
            imageMap.computeIfAbsent(image.getPostId(), key -> new ArrayList<>()).add(image.getUrl());
        }

        for (Post post : postList) {
            post.setImageList(imageMap.getOrDefault(post.getId(), Collections.emptyList()));
            post.setLiked(false);
        }

        if (userId != null) {
            List<PostLike> likes = postLikeMapper.selectList(new LambdaQueryWrapper<PostLike>()
                    .eq(PostLike::getUserId, userId)
                    .in(PostLike::getPostId, postIds));
            Set<Long> likedPostIds = likes.stream()
                    .map(PostLike::getPostId)
                    .collect(Collectors.toSet());
            for (Post post : postList) {
                post.setLiked(likedPostIds.contains(post.getId()));
            }
        }

        return postList;
    }

    public List<Map<String, Object>> toRecordItems(List<Post> postList) {
        if (postList == null || postList.isEmpty()) {
            return Collections.emptyList();
        }

        return postList.stream().map(post -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", post.getId());
            item.put("title", post.getTitle());
            item.put("username", post.getUsername());
            item.put("userId", post.getUserId());
            item.put("createTime", post.getCreateTime());
            item.put("likeCount", post.getLikeCount());
            item.put("commentCount", post.getCommentCount());
            item.put("summary", post.getSummary());
            item.put("contentPreview", post.getSummary());
            item.put("imageList", post.getImageList() == null ? Collections.emptyList() : post.getImageList());
            item.put("isLiked", post.isLiked());
            return item;
        }).toList();
    }
}
