package com.mars.post.domain.topic;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mars.post.domain.topic.Topic;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TopicMapper extends BaseMapper<Topic> {
}
