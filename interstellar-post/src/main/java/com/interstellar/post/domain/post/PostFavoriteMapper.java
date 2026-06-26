package com.interstellar.post.domain.post;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interstellar.post.domain.post.PostFavorite;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PostFavoriteMapper extends BaseMapper<PostFavorite> {
}
