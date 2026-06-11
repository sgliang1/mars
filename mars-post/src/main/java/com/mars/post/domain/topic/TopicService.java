package com.mars.post.domain.topic;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mars.post.domain.post.Post;
import com.mars.post.domain.topic.PostTopic;
import com.mars.post.domain.topic.Topic;
import com.mars.post.domain.topic.PostTopicMapper;
import com.mars.post.domain.topic.TopicMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import com.mars.post.domain.post.PostQueryService;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class TopicService {

    @Autowired
    private TopicMapper topicMapper;

    @Autowired
    private PostTopicMapper postTopicMapper;

    @Autowired
    private PostQueryService postQueryService;

    public List<Map<String, Object>> listTopics(String keyword) {
        LambdaQueryWrapper<Topic> queryWrapper = new LambdaQueryWrapper<Topic>()
                .eq(Topic::getStatus, 1)
                .orderByAsc(Topic::getSortOrder)
                .orderByDesc(Topic::getId);

        if (StringUtils.hasText(keyword)) {
            queryWrapper.and(wrapper -> wrapper
                    .like(Topic::getTitle, keyword)
                    .or()
                    .like(Topic::getSummary, keyword)
                    .or()
                    .like(Topic::getHighlight, keyword)
                    .or()
                    .like(Topic::getKeywords, keyword));
        }

        return topicMapper.selectList(queryWrapper).stream()
                .map(this::toTopicMap)
                .toList();
    }

    public Map<String, Object> getTopicDetail(String slug) {
        Topic topic = requireTopic(slug);
        return toTopicMap(topic);
    }

    public List<Post> listTopicPosts(String slug, Long userId) {
        Topic topic = requireTopic(slug);
        List<PostTopic> relations = postTopicMapper.selectList(new LambdaQueryWrapper<PostTopic>()
                .eq(PostTopic::getTopicId, topic.getId())
                .orderByDesc(PostTopic::getId));
        if (relations.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> postIds = relations.stream()
                .map(PostTopic::getPostId)
                .filter(Objects::nonNull)
                .toList();
        return postQueryService.loadPostsInOrder(postIds, userId);
    }

    private Topic requireTopic(String slug) {
        Topic topic = topicMapper.selectOne(new LambdaQueryWrapper<Topic>()
                .eq(Topic::getSlug, slug)
                .eq(Topic::getStatus, 1)
                .last("limit 1"));
        if (topic == null) {
            throw new IllegalArgumentException("Topic not found");
        }
        return topic;
    }

    private Map<String, Object> toTopicMap(Topic topic) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", topic.getId());
        data.put("slug", topic.getSlug());
        data.put("title", topic.getTitle());
        data.put("summary", topic.getSummary());
        data.put("highlight", topic.getHighlight());
        data.put("icon", topic.getIcon());
        data.put("sortOrder", topic.getSortOrder());
        data.put("status", topic.getStatus());
        data.put("keywords", splitKeywords(topic.getKeywords()));
        data.put("createdAt", topic.getCreatedAt());
        data.put("updatedAt", topic.getUpdatedAt());
        return data;
    }

    private List<String> splitKeywords(String keywords) {
        if (!StringUtils.hasText(keywords)) {
            return Collections.emptyList();
        }
        return Arrays.stream(keywords.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }
}
