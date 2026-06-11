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

    @Transactional // 微博式发布：摘要和正文分别存入两张表
    public void publish(Post post, String fullContent) {
        post.setSummary(buildSummary(fullContent));
        
        // 2. 插入主表，获取自�?ID
        postMapper.insert(post); 
        
        // 3. 关联 ID 插入详情�?
        PostContent pc = new PostContent();
        pc.setPostId(post.getId());
        pc.setContent(fullContent);
        contentMapper.insert(pc);
    }

    @Transactional
    public boolean updatePost(Long postId, Long userId, PostDTO postDTO) {
        Post post = postMapper.selectById(postId);
        if (post == null || !post.getUserId().equals(userId)) {
            return false;
        }

        String content = postDTO.getContent() == null ? "" : postDTO.getContent();
        post.setTitle(postDTO.getTitle());
        post.setSummary(buildSummary(content));
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

        return true;
    }

    @Transactional
    public boolean deletePost(Long postId, Long userId) {
        Post post = postMapper.selectById(postId);
        if (post == null || !post.getUserId().equals(userId)) {
            return false;
        }

        postLikeMapper.delete(new LambdaQueryWrapper<PostLike>()
                .eq(PostLike::getPostId, postId));
        postImageMapper.delete(new LambdaQueryWrapper<PostImage>()
                .eq(PostImage::getPostId, postId));
        commentMapper.delete(new LambdaQueryWrapper<com.mars.post.domain.comment.Comment>()
                .eq(com.mars.post.domain.comment.Comment::getPostId, postId));
        contentMapper.deleteById(postId);
        postMapper.deleteById(postId);
        return true;
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
