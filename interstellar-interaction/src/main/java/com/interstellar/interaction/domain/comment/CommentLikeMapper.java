package com.interstellar.interaction.domain.comment;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CommentLikeMapper extends BaseMapper<CommentLike> {

    @Update("UPDATE comment SET like_count = like_count + 1 WHERE id = #{commentId}")
    int incrementLikeCount(Long commentId);

    @Update("UPDATE comment SET like_count = GREATEST(like_count - 1, 0) WHERE id = #{commentId}")
    int decrementLikeCount(Long commentId);
}
