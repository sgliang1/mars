package com.mars.post.domain.post;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mars.post.domain.post.Post;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PostMapper extends BaseMapper<Post> {

    @Update("UPDATE post SET like_count = like_count + 1 WHERE id = #{postId}")
    int incrementLikeCount(Long postId);

    @Update("UPDATE post SET like_count = GREATEST(like_count - 1, 0) WHERE id = #{postId}")
    int decrementLikeCount(Long postId);

    @Update("UPDATE post SET comment_count = comment_count + 1 WHERE id = #{postId}")
    int incrementCommentCount(Long postId);
}