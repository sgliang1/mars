package com.mars.post.service;

import com.mars.post.entity.Post;
import com.mars.post.entity.PostContent;
import com.mars.post.mapper.PostContentMapper;
import com.mars.post.mapper.PostMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostService {
    @Autowired private PostMapper postMapper;
    @Autowired private PostContentMapper contentMapper;

    @Transactional // 微博式发布：摘要和正文分别存入两张表
    public void publish(Post post, String fullContent) {
        // 1. 自动截取前 140 字作为摘要
        String summary = fullContent.length() > 140 ? fullContent.substring(0, 140) + "..." : fullContent;
        post.setSummary(summary);
        
        // 2. 插入主表，获取自增 ID
        postMapper.insert(post); 
        
        // 3. 关联 ID 插入详情表
        PostContent pc = new PostContent();
        pc.setPostId(post.getId());
        pc.setContent(fullContent);
        contentMapper.insert(pc);
    }
}