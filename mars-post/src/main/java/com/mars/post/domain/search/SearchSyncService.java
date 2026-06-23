package com.mars.post.domain.search;

import com.mars.post.domain.post.Post;
import com.mars.post.domain.post.PostContent;
import com.mars.post.domain.post.PostContentMapper;
import com.mars.post.domain.post.PostMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 帖子索引同步服务
 * 当前使用直接同步方式，后续可改为 MQ 驱动
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "search.elasticsearch.enabled", havingValue = "true")
public class SearchSyncService {

    @Autowired
    private PostSearchRepository postSearchRepository;

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private PostContentMapper postContentMapper;

    /**
     * 同步单个帖子到 ES
     */
    public void syncPost(Long postId) {
        try {
            Post post = postMapper.selectById(postId);
            if (post == null) {
                postSearchRepository.deleteById(postId);
                return;
            }

            PostContent content = postContentMapper.selectById(postId);

            PostDocument doc = new PostDocument();
            doc.setId(post.getId());
            doc.setUserId(post.getUserId());
            doc.setUsername(post.getUsername());
            doc.setTitle(post.getTitle());
            doc.setContent(content != null ? content.getContent() : "");
            doc.setSummary(post.getSummary());
            doc.setLikeCount(post.getLikeCount());
            doc.setCommentCount(post.getCommentCount());
            doc.setCreateTime(post.getCreateTime());

            postSearchRepository.save(doc);
        } catch (Exception e) {
            log.warn("帖子索引同步失败: postId={}, error={}", postId, e.getMessage());
        }
    }

    /**
     * 删除帖子索引
     */
    public void deletePostIndex(Long postId) {
        try {
            postSearchRepository.deleteById(postId);
        } catch (Exception e) {
            log.warn("帖子索引删除失败: postId={}, error={}", postId, e.getMessage());
        }
    }

    /**
     * 全量重建索引（手动触发或启动时执行）
     */
    public int rebuildAll() {
        List<Post> allPosts = postMapper.selectList(null);
        List<PostDocument> documents = new ArrayList<>();

        for (Post post : allPosts) {
            PostContent content = postContentMapper.selectById(post.getId());
            PostDocument doc = new PostDocument();
            doc.setId(post.getId());
            doc.setUserId(post.getUserId());
            doc.setUsername(post.getUsername());
            doc.setTitle(post.getTitle());
            doc.setContent(content != null ? content.getContent() : "");
            doc.setSummary(post.getSummary());
            doc.setLikeCount(post.getLikeCount());
            doc.setCommentCount(post.getCommentCount());
            doc.setCreateTime(post.getCreateTime());
            documents.add(doc);
        }

        postSearchRepository.saveAll(documents);
        log.info("全量索引重建完成, 共 {} 条", documents.size());
        return documents.size();
    }
}