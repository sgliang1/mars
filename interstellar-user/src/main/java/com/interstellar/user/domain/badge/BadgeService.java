package com.interstellar.user.domain.badge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 星际勋章服务
 */
@Service
public class BadgeService {

    @Autowired private BadgeDefinitionMapper badgeDefinitionMapper;
    @Autowired private UserBadgeMapper userBadgeMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    /**
     * 颁发勋章给用户
     */
    @Transactional
    public boolean award(Long userId, Long badgeId, String source, Long sourceId) {
        // 检查是否已拥有
        Long existing = userBadgeMapper.selectCount(
                new LambdaQueryWrapper<UserBadge>()
                        .eq(UserBadge::getUserId, userId)
                        .eq(UserBadge::getBadgeId, badgeId));
        if (existing != null && existing > 0) return false;

        // 检查供应量
        BadgeDefinition def = badgeDefinitionMapper.selectById(badgeId);
        if (def == null || def.getStatus() == null || def.getStatus() != 1) return false;
        if (def.getMaxSupply() != null && def.getCurrentSupply() != null
                && def.getCurrentSupply() >= def.getMaxSupply()) return false;

        // 颁发
        UserBadge badge = new UserBadge();
        badge.setUserId(userId);
        badge.setBadgeId(badgeId);
        badge.setAwardedAt(LocalDateTime.now());
        badge.setSource(source);
        badge.setSourceId(sourceId);
        userBadgeMapper.insert(badge);

        // 更新供应量
        jdbcTemplate.update(
                "UPDATE badge_definition SET current_supply = current_supply + 1 WHERE id = ?", badgeId);

        return true;
    }

    /**
     * 获取用户已获得的勋章列表
     */
    public List<Map<String, Object>> getUserBadges(Long userId) {
        List<UserBadge> userBadges = userBadgeMapper.selectList(
                new LambdaQueryWrapper<UserBadge>()
                        .eq(UserBadge::getUserId, userId)
                        .orderByDesc(UserBadge::getAwardedAt));

        if (userBadges.isEmpty()) return Collections.emptyList();

        Set<Long> badgeIds = userBadges.stream().map(UserBadge::getBadgeId).collect(Collectors.toSet());
        Map<Long, BadgeDefinition> definitions = badgeDefinitionMapper.selectList(
                new LambdaQueryWrapper<BadgeDefinition>().in(BadgeDefinition::getId, badgeIds))
                .stream().collect(Collectors.toMap(BadgeDefinition::getId, d -> d));

        return userBadges.stream().map(ub -> {
            BadgeDefinition def = definitions.get(ub.getBadgeId());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("badgeId", ub.getBadgeId().toString());
            data.put("name", def != null ? def.getName() : "未知勋章");
            data.put("description", def != null ? def.getDescription() : "");
            data.put("iconUrl", def != null ? def.getIconUrl() : "");
            data.put("rarity", def != null ? def.getRarity() : "common");
            data.put("category", def != null ? def.getCategory() : "");
            data.put("awardedAt", ub.getAwardedAt() != null ? ub.getAwardedAt().toString() : "");
            data.put("source", ub.getSource() != null ? ub.getSource() : "");
            return data;
        }).toList();
    }

    /**
     * 获取勋章墙（分组展示）
     */
    public Map<String, List<Map<String, Object>>> getBadgeWall(Long userId) {
        List<Map<String, Object>> badges = getUserBadges(userId);
        return badges.stream().collect(Collectors.groupingBy(
                m -> m.get("category") != null ? m.get("category").toString() : "other"));
    }

    /**
     * 获取所有勋章目录（含用户是否已获得）
     */
    public List<Map<String, Object>> getCatalog(Long userId) {
        List<BadgeDefinition> allDefs = badgeDefinitionMapper.selectList(
                new LambdaQueryWrapper<BadgeDefinition>()
                        .eq(BadgeDefinition::getStatus, 1)
                        .orderByAsc(BadgeDefinition::getCategory)
                        .orderByAsc(BadgeDefinition::getId));

        Set<Long> ownedIds = userBadgeMapper.selectList(
                new LambdaQueryWrapper<UserBadge>().eq(UserBadge::getUserId, userId))
                .stream().map(UserBadge::getBadgeId).collect(Collectors.toSet());

        return allDefs.stream().map(def -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", def.getId().toString());
            data.put("name", def.getName());
            data.put("description", def.getDescription());
            data.put("iconUrl", def.getIconUrl());
            data.put("rarity", def.getRarity());
            data.put("category", def.getCategory());
            data.put("maxSupply", def.getMaxSupply());
            data.put("currentSupply", def.getCurrentSupply());
            data.put("owned", ownedIds.contains(def.getId()));
            return data;
        }).toList();
    }
}
