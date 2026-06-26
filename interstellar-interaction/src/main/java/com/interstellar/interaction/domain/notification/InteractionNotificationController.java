package com.interstellar.interaction.domain.notification;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interstellar.common.Result;
import com.interstellar.interaction.domain.filter.UserFilterService;
import com.interstellar.interaction.domain.post.PostEntity;
import com.interstellar.interaction.domain.post.PostMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import com.interstellar.common.model.Notification;
import java.util.*;

@RestController
@RequestMapping("/notification")
public class InteractionNotificationController {

    @Autowired
    private InteractionNotificationMapper notificationMapper;

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private UserFilterService userFilterService;

    @GetMapping("/interactions")
    public Result<List<Map<String, Object>>> list(
            @RequestHeader("X-User-Id") String userIdStr,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "50") int size) {

        Long userId = Long.parseLong(userIdStr);

        List<Notification> notifications = notificationMapper.selectList(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getCategory, "interaction")
                        .orderByDesc(Notification::getCreatedAt)
                        .last("LIMIT " + size + " OFFSET " + (page - 1) * size));

        if (notifications.isEmpty()) {
            return Result.success(Collections.emptyList());
        }

        Set<Long> postIds = new HashSet<>();
        for (Notification n : notifications) {
            if ("like".equals(n.getSourceType()) || "comment".equals(n.getSourceType())) {
                Long pid = parsePostId(n);
                if (pid != null) postIds.add(pid);
            }
        }

        Map<Long, String> postTitleMap = new HashMap<>();
        if (!postIds.isEmpty()) {
            List<PostEntity> posts = postMapper.selectBatchIds(postIds);
            for (PostEntity p : posts) {
                postTitleMap.put(p.getId(), p.getTitle());
            }
        }

        Set<Long> filteredIds = userFilterService.getFilteredIds(userId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Notification n : notifications) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", String.valueOf(n.getId()));
            item.put("type", n.getSourceType());

            Map<String, String> contentMap = parseContent(n.getContent());

            if (!filteredIds.isEmpty()) {
                Long actorId = parseLong(contentMap.get("actorId"));
                if (actorId != null && filteredIds.contains(actorId)) {
                    continue;
                }
            }

            item.put("actorId", contentMap.getOrDefault("actorId", ""));
            item.put("actorName", n.getTitle());
            item.put("actorAvatar", "");

            String postId = contentMap.get("postId");
            if (postId != null && !postId.isEmpty()) {
                item.put("postId", postId);
                String postTitle = contentMap.get("postTitle");
                if (postTitle == null || postTitle.isEmpty()) {
                    postTitle = postTitleMap.get(parseLong(postId));
                }
                item.put("postTitle", postTitle);
            }

            String commentContent = contentMap.get("commentContent");
            if (commentContent != null && !commentContent.isEmpty()) {
                item.put("commentContent", commentContent);
            }

            item.put("createdAt", n.getCreatedAt() != null ? n.getCreatedAt().toString() : "");
            item.put("isRead", n.getReadStatus() != null && n.getReadStatus() == 1);

            result.add(item);
        }

        return Result.success(aggregateNotifications(result));
    }

    private List<Map<String, Object>> aggregateNotifications(List<Map<String, Object>> items) {
        Map<String, Map<String, Object>> likeGroups = new LinkedHashMap<>();
        List<Map<String, Object>> others = new ArrayList<>();

        for (Map<String, Object> item : items) {
            String type = item.get("type") != null ? item.get("type").toString() : "";
            String postId = item.get("postId") != null ? item.get("postId").toString() : "";

            if ("like".equals(type) && !postId.isEmpty()) {
                String groupKey = "like:" + postId;
                Map<String, Object> existing = likeGroups.get(groupKey);
                if (existing == null) {
                    Map<String, Object> group = new LinkedHashMap<>(item);
                    group.put("actorCount", 1);
                    List<Map<String, String>> actors = new ArrayList<>();
                    Map<String, String> actor = new LinkedHashMap<>();
                    actor.put("actorId", item.get("actorId") != null ? item.get("actorId").toString() : "");
                    actor.put("actorName", item.get("actorName") != null ? item.get("actorName").toString() : "");
                    actors.add(actor);
                    group.put("actors", actors);
                    likeGroups.put(groupKey, group);
                } else {
                    int count = (int) existing.get("actorCount") + 1;
                    existing.put("actorCount", count);
                    @SuppressWarnings("unchecked")
                    List<Map<String, String>> actors = (List<Map<String, String>>) existing.get("actors");
                    Map<String, String> actor = new LinkedHashMap<>();
                    actor.put("actorId", item.get("actorId") != null ? item.get("actorId").toString() : "");
                    actor.put("actorName", item.get("actorName") != null ? item.get("actorName").toString() : "");
                    actors.add(actor);
                    existing.put("createdAt", item.get("createdAt"));
                }
            } else {
                others.add(item);
            }
        }

        List<Map<String, Object>> merged = new ArrayList<>(likeGroups.values());
        merged.addAll(others);
        merged.sort((a, b) -> {
            String ta = a.get("createdAt") != null ? a.get("createdAt").toString() : "";
            String tb = b.get("createdAt") != null ? b.get("createdAt").toString() : "";
            return tb.compareTo(ta);
        });
        return merged;
    }

    @GetMapping("/interactions/unread-count")
    public Result<Map<String, Object>> unreadCount(@RequestHeader("X-User-Id") String userIdStr) {
        Long userId = Long.parseLong(userIdStr);
        Long count = notificationMapper.selectCount(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getCategory, "interaction")
                        .eq(Notification::getReadStatus, 0));
        Map<String, Object> data = new HashMap<>();
        data.put("count", count);
        return Result.success(data);
    }

    @PostMapping("/interactions/{id}/read")
    public Result<String> markRead(@PathVariable("id") Long id,
                                   @RequestHeader("X-User-Id") String userIdStr) {
        Long userId = Long.parseLong(userIdStr);
        Notification n = notificationMapper.selectById(id);
        if (n == null || !n.getUserId().equals(userId)) {
            return Result.fail("通知不存在");
        }
        if (n.getReadStatus() == 0) {
            n.setReadStatus(1);
            n.setReadAt(LocalDateTime.now());
            notificationMapper.updateById(n);
        }
        return Result.successMessage("已标记已读");
    }

    @PostMapping("/interactions/read-all")
    public Result<String> markAllRead(@RequestHeader("X-User-Id") String userIdStr) {
        Long userId = Long.parseLong(userIdStr);
        notificationMapper.markAllInteractionReadByUserId(userId);
        return Result.successMessage("已全部标记已读");
    }

    // ---- 工具方法 ----

    private Map<String, String> parseContent(String content) {
        Map<String, String> map = new HashMap<>();
        if (content == null || content.isEmpty()) return map;
        content = content.trim();
        if (content.startsWith("{") && content.endsWith("}")) {
            content = content.substring(1, content.length() - 1);
            for (String pair : content.split(",")) {
                String[] kv = pair.split(":", 2);
                if (kv.length == 2) {
                    String key = kv[0].trim().replace("\"", "");
                    String value = kv[1].trim().replace("\"", "");
                    map.put(key, value);
                }
            }
        }
        return map;
    }

    private Long parsePostId(Notification n) {
        Map<String, String> content = parseContent(n.getContent());
        return parseLong(content.get("postId"));
    }

    private Long parseLong(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
