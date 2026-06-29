package com.interstellar.post.domain.broadcast;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BroadcastService {

    @Autowired private BroadcastMapper broadcastMapper;
    @Autowired private BroadcastReadMapper broadcastReadMapper;

    /**
     * 获取已发布的广播列表（带已读状态）
     */
    public List<Map<String, Object>> listPublished(Long userId, int page, int size) {
        List<Broadcast> broadcasts = broadcastMapper.selectList(
                new LambdaQueryWrapper<Broadcast>()
                        .eq(Broadcast::getStatus, 1)
                        .le(Broadcast::getPublishTime, LocalDateTime.now())
                        .and(w -> w.isNull(Broadcast::getExpireTime)
                                .or()
                                .ge(Broadcast::getExpireTime, LocalDateTime.now()))
                        .orderByDesc(Broadcast::getPublishTime)
                        .last("LIMIT " + size + " OFFSET " + ((page - 1) * size)));

        if (broadcasts.isEmpty()) return Collections.emptyList();

        // 查询当前用户已读状态
        Set<Long> readIds = broadcastReadMapper.selectList(
                new LambdaQueryWrapper<BroadcastRead>()
                        .eq(BroadcastRead::getUserId, userId)
                        .in(BroadcastRead::getBroadcastId, broadcasts.stream().map(Broadcast::getId).collect(Collectors.toList())))
                .stream().map(BroadcastRead::getBroadcastId).collect(Collectors.toSet());

        return broadcasts.stream().map(b -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", b.getId().toString());
            data.put("title", b.getTitle());
            data.put("content", b.getContent());
            data.put("coverImage", b.getCoverImage());
            data.put("linkType", b.getLinkType());
            data.put("linkValue", b.getLinkValue());
            data.put("publishTime", b.getPublishTime() != null ? b.getPublishTime().toString() : "");
            data.put("read", readIds.contains(b.getId()));
            return data;
        }).toList();
    }

    /**
     * 标记已读
     */
    public void markAsRead(Long userId, Long broadcastId) {
        Long count = broadcastReadMapper.selectCount(
                new LambdaQueryWrapper<BroadcastRead>()
                        .eq(BroadcastRead::getUserId, userId)
                        .eq(BroadcastRead::getBroadcastId, broadcastId));
        if (count != null && count > 0) return;

        BroadcastRead read = new BroadcastRead();
        read.setUserId(userId);
        read.setBroadcastId(broadcastId);
        read.setReadAt(LocalDateTime.now());
        broadcastReadMapper.insert(read);
    }

    /**
     * 获取未读数量
     */
    public int getUnreadCount(Long userId) {
        // 已发布的广播总数
        Long total = broadcastMapper.selectCount(
                new LambdaQueryWrapper<Broadcast>()
                        .eq(Broadcast::getStatus, 1)
                        .le(Broadcast::getPublishTime, LocalDateTime.now())
                        .and(w -> w.isNull(Broadcast::getExpireTime)
                                .or()
                                .ge(Broadcast::getExpireTime, LocalDateTime.now())));
        if (total == null || total == 0) return 0;

        // 已读数量
        Long readCount = broadcastReadMapper.selectCount(
                new LambdaQueryWrapper<BroadcastRead>()
                        .eq(BroadcastRead::getUserId, userId));
        if (readCount == null) readCount = 0L;

        return (int) (total - readCount);
    }
}
