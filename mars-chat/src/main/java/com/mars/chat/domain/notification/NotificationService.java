package com.mars.chat.domain.notification;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mars.common.model.Notification;
import com.mars.chat.domain.notification.NotificationMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class NotificationService {

    @Autowired
    private NotificationMapper notificationMapper;

    public List<Map<String, Object>> listNotifications(Long userId) {
        return selectNotifications(userId, true).stream()
                .map(this::toMap)
                .toList();
    }

    @Transactional
    public Map<String, Object> markRead(Long userId, Long id) {
        Notification notification = requireNotification(userId, id);
        notification.setReadStatus(1);
        notification.setReadAt(LocalDateTime.now());
        notificationMapper.updateById(notification);
        return toMap(notification);
    }

    @Transactional
    public int markAllRead(Long userId) {
        return notificationMapper.markAllReadByUserId(userId);
    }

    public int countUnread(Long userId) {
        ensureDefaultNotifications(userId);
        Long count = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getReadStatus, 0));
        return count == null ? 0 : count.intValue();
    }

    public String latestPreview(Long userId) {
        Notification latest = selectLatestNotification(userId);
        if (latest == null) {
            return "\u7cfb\u7edf\u901a\u77e5\u4f1a\u96c6\u4e2d\u5c55\u793a\u5728\u8fd9\u91cc\uff0c\u8fdb\u5165\u540e\u4f1a\u6e05\u7a7a\u672a\u8bfb\u3002";
        }
        return buildPreview(latest);
    }

    public List<Map<String, Object>> listConversationMessages(Long userId) {
        return selectNotifications(userId, false).stream()
                .map(this::toConversationMessage)
                .toList();
    }

    @Transactional
    public void ensureDefaultNotifications(Long userId) {
        Long count = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId));
        if (count != null && count > 0) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        insertNotification(
                userId,
                "account",
                "\u8d26\u53f7\u5b89\u5168\u63d0\u9192",
                "\u8d26\u53f7\u5b89\u5168\u63d0\u9192\u5df2\u96c6\u4e2d\u5230\u8fd9\u91cc\u67e5\u770b\uff0c\u53ef\u6309\u6765\u6e90\u9759\u97f3\u3002",
                "security",
                "account-security",
                0,
                now.minusHours(5)
        );
        insertNotification(
                userId,
                "release",
                "\u7248\u672c\u66f4\u65b0\u8bf4\u660e",
                "\u7248\u672c\u66f4\u65b0\u8bf4\u660e\u5df2\u53d1\u5e03\uff0c\u672a\u8bfb\u8fdb\u5165\u4f1a\u8bdd\u540e\u81ea\u52a8\u6e05\u96f6\u3002",
                "release",
                "release-notes",
                0,
                now.minusMinutes(35)
        );
    }

    private List<Notification> selectNotifications(Long userId, boolean newestFirst) {
        LambdaQueryWrapper<Notification> query = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId);
        if (newestFirst) {
            query.orderByDesc(Notification::getCreatedAt)
                    .orderByDesc(Notification::getId);
        } else {
            query.orderByAsc(Notification::getCreatedAt)
                    .orderByAsc(Notification::getId);
        }
        return notificationMapper.selectList(query);
    }

    private Notification selectLatestNotification(Long userId) {
        return notificationMapper.selectOne(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .orderByDesc(Notification::getCreatedAt)
                .orderByDesc(Notification::getId)
                .last("limit 1"));
    }

    private void insertNotification(Long userId, String category, String title, String content, String sourceType, String sourceId, int readStatus, LocalDateTime createdAt) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setCategory(category);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setSourceType(sourceType);
        notification.setSourceId(sourceId);
        notification.setReadStatus(readStatus);
        notification.setCreatedAt(createdAt);
        if (readStatus == 1) {
            notification.setReadAt(createdAt);
        }
        notificationMapper.insert(notification);
    }

    private Map<String, Object> toConversationMessage(Notification notification) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", notification.getId() == null ? "" : notification.getId().toString());
        data.put("senderId", "system");
        data.put("senderName", "\u7cfb\u7edf");
        data.put("content", buildPreview(notification));
        data.put("createTime", notification.getCreatedAt() == null ? "" : notification.getCreatedAt().toString());
        data.put("type", 1);
        data.put("deliveryStatus", notification.getReadStatus() != null && notification.getReadStatus() == 1 ? "read" : "sent");
        return data;
    }

    private String buildPreview(Notification notification) {
        String title = notification.getTitle() == null ? "" : notification.getTitle().trim();
        String content = notification.getContent() == null ? "" : notification.getContent().trim();
        if (!StringUtils.hasText(content)) {
            return title;
        }
        if (!StringUtils.hasText(title) || title.equals(content)) {
            return content;
        }
        return title + "\n" + content;
    }

    private Notification requireNotification(Long userId, Long id) {
        Notification notification = notificationMapper.selectById(id);
        if (notification == null || notification.getUserId() == null || !notification.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Notification not found");
        }
        return notification;
    }

    private Map<String, Object> toMap(Notification notification) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", notification.getId());
        data.put("category", notification.getCategory());
        data.put("title", notification.getTitle());
        data.put("content", notification.getContent());
        data.put("sourceType", notification.getSourceType());
        data.put("sourceId", notification.getSourceId());
        data.put("readStatus", notification.getReadStatus());
        data.put("createdAt", notification.getCreatedAt() == null ? "" : notification.getCreatedAt().toString());
        data.put("readAt", notification.getReadAt() == null ? "" : notification.getReadAt().toString());
        return data;
    }
}
