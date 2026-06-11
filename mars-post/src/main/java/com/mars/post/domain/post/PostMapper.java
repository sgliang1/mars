package com.mars.post.domain.post;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mars.post.domain.post.Post;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PostMapper extends BaseMapper<Post> {}
