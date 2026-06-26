package com.interstellar.relation.domain.relation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class RelationGroupService {

    @Autowired
    private RelationGroupMapper groupMapper;

    @Autowired
    private RelationGroupMemberMapper memberMapper;

    @Autowired
    private UserRelationMapper userRelationMapper;

    public List<RelationGroup> listGroups(Long userId) {
        return groupMapper.selectList(new LambdaQueryWrapper<RelationGroup>()
                .eq(RelationGroup::getUserId, userId)
                .orderByAsc(RelationGroup::getSortOrder));
    }

    public RelationGroup createGroup(Long userId, String name, String icon) {
        RelationGroup group = new RelationGroup();
        group.setUserId(userId);
        group.setName(name);
        group.setIcon(icon);
        group.setSortOrder(0);
        group.setCreatedAt(LocalDateTime.now());
        groupMapper.insert(group);
        return group;
    }

    public void updateGroup(Long groupId, Long userId, String name, String icon, Integer sortOrder) {
        RelationGroup group = groupMapper.selectById(groupId);
        if (group == null || !group.getUserId().equals(userId)) {
            throw new IllegalArgumentException("分组不存在或无权操作");
        }
        if (name != null) group.setName(name);
        if (icon != null) group.setIcon(icon);
        if (sortOrder != null) group.setSortOrder(sortOrder);
        groupMapper.updateById(group);
    }

    public void deleteGroup(Long groupId, Long userId) {
        RelationGroup group = groupMapper.selectById(groupId);
        if (group == null || !group.getUserId().equals(userId)) {
            throw new IllegalArgumentException("分组不存在或无权操作");
        }
        // 先删成员
        memberMapper.delete(new LambdaQueryWrapper<RelationGroupMember>()
                .eq(RelationGroupMember::getGroupId, groupId));
        groupMapper.deleteById(groupId);
    }

    @Transactional
    public void addMember(Long groupId, Long userId, Long relationId) {
        RelationGroup group = groupMapper.selectById(groupId);
        if (group == null || !group.getUserId().equals(userId)) {
            throw new IllegalArgumentException("分组不存在或无权操作");
        }
        Long count = memberMapper.selectCount(new LambdaQueryWrapper<RelationGroupMember>()
                .eq(RelationGroupMember::getGroupId, groupId)
                .eq(RelationGroupMember::getRelationId, relationId));
        if (count > 0) return;

        RelationGroupMember member = new RelationGroupMember();
        member.setGroupId(groupId);
        member.setRelationId(relationId);
        member.setCreatedAt(LocalDateTime.now());
        memberMapper.insert(member);
    }

    @Transactional
    public void addMemberByTargetUserId(Long groupId, Long userId, Long targetUserId) {
        RelationGroup group = groupMapper.selectById(groupId);
        if (group == null || !group.getUserId().equals(userId)) {
            throw new IllegalArgumentException("分组不存在或无权操作");
        }
        // 查找 relation_id
        UserRelation relation = userRelationMapper.selectOne(new LambdaQueryWrapper<UserRelation>()
                .eq(UserRelation::getFollowerId, userId)
                .eq(UserRelation::getFollowedId, targetUserId));
        if (relation == null) {
            throw new IllegalArgumentException("未关注该用户");
        }
        addMember(groupId, userId, relation.getId());
    }

    public void removeMember(Long groupId, Long userId, Long relationId) {
        RelationGroup group = groupMapper.selectById(groupId);
        if (group == null || !group.getUserId().equals(userId)) {
            throw new IllegalArgumentException("分组不存在或无权操作");
        }
        memberMapper.delete(new LambdaQueryWrapper<RelationGroupMember>()
                .eq(RelationGroupMember::getGroupId, groupId)
                .eq(RelationGroupMember::getRelationId, relationId));
    }

    public void removeMemberByTargetUserId(Long groupId, Long userId, Long targetUserId) {
        RelationGroup group = groupMapper.selectById(groupId);
        if (group == null || !group.getUserId().equals(userId)) {
            throw new IllegalArgumentException("分组不存在或无权操作");
        }
        UserRelation relation = userRelationMapper.selectOne(new LambdaQueryWrapper<UserRelation>()
                .eq(UserRelation::getFollowerId, userId)
                .eq(UserRelation::getFollowedId, targetUserId));
        if (relation != null) {
            removeMember(groupId, userId, relation.getId());
        }
    }

    public List<Map<String, Object>> getGroupFollowingList(Long groupId, Long userId) {
        RelationGroup group = groupMapper.selectById(groupId);
        if (group == null || !group.getUserId().equals(userId)) {
            throw new IllegalArgumentException("分组不存在或无权操作");
        }
        return userRelationMapper.selectFollowingByGroupId(groupId);
    }
}