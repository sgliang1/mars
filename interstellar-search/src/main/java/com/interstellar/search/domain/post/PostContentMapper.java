package com.interstellar.search.domain.post;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 帖子内容 Mapper（只读，共享数据库）
 */
@Mapper
public interface PostContentMapper extends BaseMapper<PostContent> {
}
