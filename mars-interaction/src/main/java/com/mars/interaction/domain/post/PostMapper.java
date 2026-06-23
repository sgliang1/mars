package com.mars.interaction.domain.post;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

/**
 * 帖子 Mapper（只读，共享数据库）
 */
@Mapper
public interface PostMapper extends BaseMapper<PostEntity> {

    @Update("UPDATE post SET comment_count = comment_count + 1 WHERE id = #{postId}")
    int incrementCommentCount(Long postId);
}
