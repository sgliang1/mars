package com.mars.search.domain.post;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 帖子 Mapper（只读，共享数据库）
 */
@Mapper
public interface PostMapper extends BaseMapper<PostEntity> {
}
