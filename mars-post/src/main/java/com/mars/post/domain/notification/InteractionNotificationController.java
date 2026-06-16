package com.mars.post.domain.notification;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mars.common.Result;
import com.mars.post.domain.post.Post;
import com.mars.post.domain.post.PostMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import com.mars.common.model.Notification;
import java.util.*;

@RestController
@RequestMapping("/notification")
public class InteractionNotificationController {

    @Autowired
    private InteractionNotificationMapper notificationMapper;

    @Autowired
    private PostMapper postMapper;

    /**
     * 获取互动通知列表
     */
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

        // 批量收集需要查询的 postId
        Set<Long> postIds = new HashSet<>();
        for (Notification n : notifications) {
            if ("like".equals(n.getSourceType()) || "comment".equals(n.getSourceType())) {
                Long pid = parsePostId(n);
                if (pid != null) postIds.add(pid);
            }
        }

        // 批量查帖子标题
        Map<Long, String> postTitleMap = new HashMap<>();
        if (!postIds.isEmpty()) {
            List<Post> posts = postMapper.selectBatchIds(postIds);
            for (Post p : posts) {
                postTitleMap.put(p.getId(), p.getTitle());
            }
        }

        // 组装前端需要的格式
        List<Map<String, Object>> result = new ArrayList<>();
        for (Notification n : notifications) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", String.valueOf(n.getId()));
            item.put("type", n.getSourceType());

            // 从 content JSON 解析
            Map<String, String> contentMap = parseContent(n.getContent());
            item.put("actorId", contentMap.getOrDefault("actorId", ""));
            item.put("actorName", n.getTitle()); // title 存的是 actorName
            item.put("actorAvatar", "");

            String postId = contentMap.get("postId");
            if (postId != null && !postId.isEmpty()) {
                item.put("postId", postId);
                // 优先用 content 里的 postTitle，其次用查到的
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

        return Result.success(result);
    }

    /**
     * 获取未读数量
     */
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

    /**
     * 标记单条已读
     */
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

    /**
     * 全部标记已读
     */
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
        // 简单 JSON 解析：{"actorId":"123","postId":"456",...}
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