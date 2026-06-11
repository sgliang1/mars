package com.mars.post.domain.comment;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mars.post.domain.comment.Comment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CommentMapper extends BaseMapper<Comment> {
}
