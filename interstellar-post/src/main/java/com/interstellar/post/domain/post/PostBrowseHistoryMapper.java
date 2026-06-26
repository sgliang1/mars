package com.interstellar.post.domain.post;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interstellar.post.domain.post.PostBrowseHistory;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PostBrowseHistoryMapper extends BaseMapper<PostBrowseHistory> {

    @Delete("DELETE FROM post_browse_history WHERE user_id = #{userId} " +
            "AND id NOT IN (SELECT id FROM (SELECT id FROM post_browse_history " +
            "WHERE user_id = #{userId} ORDER BY last_viewed_at DESC, id DESC LIMIT #{keepCount}) AS t)")
    int trimHistory(@Param("userId") Long userId, @Param("keepCount") int keepCount);
}