package com.mars.post.domain.post;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mars.common.cache.CacheKeys;
import com.mars.common.cache.CacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class RepostService {

    @Autowired private PostRepostMapper repostMapper;
    @Autowired private PostMapper postMapper;
    @Autowired private CacheService cacheService;

    /**
     * 创建转发帖
     * @return 转发记录，若已转发过返回 null
     */
    @Transactional
    public PostRepost repost(Long userId, Long originalPostId, String quoteContent) {
        // 校验原帖存在
        Post original = postMapper.selectById(originalPostId);
        if (original == null) {
            throw new IllegalArgumentException("原帖不存在");
        }
        // 不可转发自己的帖子（可选，微博允许，这里先允许）
        // 检查是否已转发
        Long count = repostMapper.selectCount(new LambdaQueryWrapper<PostRepost>()
                .eq(PostRepost::getUserId, userId)
                .eq(PostRepost::getOriginalPostId, originalPostId));
        if (count > 0) {
            return null; // 已转发过
        }

        PostRepost repost = new PostRepost();
        repost.setUserId(userId);
        repost.setOriginalPostId(originalPostId);
        repost.setQuoteContent(quoteContent != null ? quoteContent : "");
        repost.setCreateTime(LocalDateTime.now());
        repostMapper.insert(repost);

        // 原子递增原帖 share_count
        postMapper.incrementShareCount(originalPostId);
        cacheService.delete(CacheKeys.key(CacheKeys.POST_DETAIL, originalPostId));

        return repost;
    }

    /**
     * 获取帖子的转发列表
     */
    public List<PostRepost> listByPost(Long postId, int page, int size) {
        return repostMapper.selectList(new LambdaQueryWrapper<PostRepost>()
                .eq(PostRepost::getOriginalPostId, postId)
                .orderByDesc(PostRepost::getCreateTime)
                .last("LIMIT " + size + " OFFSET " + (page - 1) * size));
    }

    /**
     * 获取用户的转发列表
     */
    public List<PostRepost> listByUser(Long userId, int page, int size) {
        return repostMapper.selectList(new LambdaQueryWrapper<PostRepost>()
                .eq(PostRepost::getUserId, userId)
                .orderByDesc(PostRepost::getCreateTime)
                .last("LIMIT " + size + " OFFSET " + (page - 1) * size));
    }
}