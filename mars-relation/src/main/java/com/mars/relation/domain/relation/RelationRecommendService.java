package com.mars.relation.domain.relation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class RelationRecommendService {

    @Autowired
    private UserRelationMapper userRelationMapper;

    /**
     * "你可能认识的人" —— 基于二度好友（好友的好友），按共同好友数降序
     */
    public List<Map<String, Object>> recommend(Long userId, int limit) {
        if (limit <= 0 || limit > 50) limit = 20;
        return userRelationMapper.selectRecommendUsers(userId, limit);
    }
}