package com.interstellar.relation.domain.relation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class RelationEventService {

    @Autowired
    private UserRelationEventMapper eventMapper;

    @Async
    public void recordEvent(Long userId, Long targetUserId, String eventType) {
        UserRelationEvent event = new UserRelationEvent();
        event.setUserId(userId);
        event.setTargetUserId(targetUserId);
        event.setEventType(eventType);
        event.setCreatedAt(LocalDateTime.now());
        eventMapper.insert(event);
    }

    public List<UserRelationEvent> getEvents(Long targetUserId, String eventType, LocalDateTime since, int limit) {
        LambdaQueryWrapper<UserRelationEvent> wrapper = new LambdaQueryWrapper<UserRelationEvent>()
                .eq(UserRelationEvent::getTargetUserId, targetUserId);
        if (eventType != null) {
            wrapper.eq(UserRelationEvent::getEventType, eventType);
        }
        if (since != null) {
            wrapper.ge(UserRelationEvent::getCreatedAt, since);
        }
        wrapper.orderByDesc(UserRelationEvent::getCreatedAt)
                .last("LIMIT " + limit);
        return eventMapper.selectList(wrapper);
    }

    public int cleanOlderThan(int days) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        return eventMapper.delete(new LambdaQueryWrapper<UserRelationEvent>()
                .lt(UserRelationEvent::getCreatedAt, cutoff));
    }
}