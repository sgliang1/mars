package com.mars.post.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mars.common.cache.CacheService;
import com.mars.post.domain.post.Post;
import com.mars.post.domain.post.PostMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时发布任务
 * 每分钟扫描一次，将到达发布时间的帖子自动发布
 */
@Component
class ScheduledPostTask {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledPostTask.class);

    @Autowired private PostMapper postMapper;
    @Autowired private CacheService cacheService;

    /**
     * 每分钟执行一次，发布定时帖子
     */
    @Scheduled(cron = "0 * * * * ?")
    @Transactional
    public void publishScheduledPosts() {
        // 分布式锁，防止多实例重复执行
        String lockOwner = cacheService.tryLock("lock:scheduled-post-task", Duration.ofSeconds(55));
        if (lockOwner == null) {
            logger.debug("定时发布任务未获取到锁，跳过本次执行");
            return;
        }
        try {
            LocalDateTime now = LocalDateTime.now();

            // 查询所有到达发布时间的定时帖子
            List<Post> scheduledPosts = postMapper.selectList(
                    new LambdaQueryWrapper<Post>()
                            .eq(Post::getDisplayStatus, 0)      // 待发布
                            .isNotNull(Post::getScheduledAt)
                            .le(Post::getScheduledAt, now)     // scheduled_at <= now
            );

            if (scheduledPosts.isEmpty()) return;

            int published = 0;
            for (Post post : scheduledPosts) {
                post.setDisplayStatus(1);   // 1 = 已发布
                post.setAuditStatus(2);     // 2 = 人审通过（定时帖子自动通过审核）
                post.setScheduledAt(null);
                postMapper.updateById(post);

                published++;
                logger.info("定时帖子已发布: postId={}, userId={}", post.getId(), post.getUserId());
            }

            logger.info("本轮定时发布完成，共发布 {} 篇帖子", published);
        } finally {
            cacheService.unlock("lock:scheduled-post-task", lockOwner);
        }
    }
}