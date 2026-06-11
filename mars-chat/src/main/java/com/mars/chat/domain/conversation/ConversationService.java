package com.mars.chat.domain.conversation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mars.chat.domain.conversation.Conversation;
import com.mars.chat.domain.conversation.ConversationMember;
import com.mars.chat.domain.message.ConversationMessage;
import com.mars.chat.domain.conversation.ConversationMapper;
import com.mars.chat.domain.conversation.ConversationMemberMapper;
import com.mars.chat.domain.message.ConversationMessageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.mars.chat.domain.notification.NotificationService;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ConversationService {

    private static final String TYPE_PUBLIC = "public_channel";
    private static final String TYPE_DIRECT = "direct";
    private static final String TYPE_GROUP = "group";
    private static final String TYPE_SYSTEM = "system";

    @Autowired
    private ConversationMapper conversationMapper;

    @Autowired
    private ConversationMemberMapper conversationMemberMapper;

    @Autowired
    private ConversationMessageMapper conversationMessageMapper;

    @Autowired
    private NotificationService notificationService;

    public List<Map<String, Object>> listSummaries(Long userId, String scope) {
        ensureDefaultConversations(userId);

        List<ConversationMember> members = conversationMemberMapper.selectList(new LambdaQueryWrapper<ConversationMember>()
                .eq(ConversationMember::getUserId, userId));
        if (members.isEmpty()) {
            return List.of();
        }

        Map<Long, ConversationMember> memberMap = new LinkedHashMap<>();
        for (ConversationMember member : members) {
            if (member.getConversationId() != null) {
                memberMap.put(member.getConversationId(), member);
            }
        }
        if (memberMap.isEmpty()) {
            return List.of();
        }

        LambdaQueryWrapper<Conversation> query = new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getStatus, 1)
                .in(Conversation::getId, memberMap.keySet())
                .orderByDesc(Conversation::getLastMessageAt)
                .orderByDesc(Conversation::getUpdatedAt)
                .orderByDesc(Conversation::getId);

        if ("session".equals(scope)) {
            query.in(Conversation::getType, TYPE_PUBLIC, TYPE_DIRECT, TYPE_GROUP);
        } else if ("notification".equals(scope)) {
            query.eq(Conversation::getType, TYPE_SYSTEM);
        }

        return conversationMapper.selectList(query).stream()
                .map(conversation -> toSummaryMap(conversation, memberMap.get(conversation.getId()), userId))
                .toList();
    }

    public Map<String, Object> getSummary(Long userId, Long conversationId) {
        ensureDefaultConversations(userId);
        Conversation conversation = requireConversation(conversationId);
        return toSummaryMap(conversation, requireMember(conversation.getId(), userId), userId);
    }

    public List<Map<String, Object>> listMessages(Long userId, Long conversationId) {
        ensureDefaultConversations(userId);
        Conversation conversation = requireConversation(conversationId);
        requireMember(conversationId, userId);
        if (TYPE_SYSTEM.equals(conversation.getType())) {
            return notificationService.listConversationMessages(userId);
        }
        return conversationMessageMapper.selectList(new LambdaQueryWrapper<ConversationMessage>()
                        .eq(ConversationMessage::getConversationId, conversationId)
                        .orderByAsc(ConversationMessage::getCreatedAt)
                        .orderByAsc(ConversationMessage::getId))
                .stream()
                .map(this::toMessageMap)
                .toList();
    }

    @Transactional
    public Map<String, Object> ensureDirectConversation(Long userId, String targetUserId, String username, String currentUsername) {
        String safeTargetId = StringUtils.hasText(targetUserId) ? targetUserId.trim() : "unknown";
        String safeTargetName = StringUtils.hasText(username) ? username.trim() : "私信对象";
        String safeCurrentName = StringUtils.hasText(currentUsername) ? currentUsername.trim() : "我";
        String directMetadata = buildDirectMetadata(userId, safeCurrentName, safeTargetId, safeTargetName);
        String defaultDescription = "进入私信后可直接继续对话。";
        LocalDateTime now = LocalDateTime.now();
        Conversation conversation = ensureConversation(
                TYPE_DIRECT,
                buildDirectBizKey(userId, safeTargetId),
                directMetadata,
                defaultDescription,
                now
        );

        boolean changed = false;
        if (!directMetadata.equals(conversation.getTitle())) {
            conversation.setTitle(directMetadata);
            changed = true;
        }
        if (!defaultDescription.equals(conversation.getDescription())) {
            conversation.setDescription(defaultDescription);
            changed = true;
        }
        if (changed) {
            conversation.setUpdatedAt(now);
            conversationMapper.updateById(conversation);
        }

        ensureMember(conversation.getId(), userId);
        Long targetId = parseLongOrNull(safeTargetId);
        if (targetId != null && !targetId.equals(userId)) {
            ensureMember(conversation.getId(), targetId);
        }
        return getSummary(userId, conversation.getId());
    }

    @Transactional
    public Map<String, Object> ensureTopicGroupConversation(Long userId, String topicSlug, String title) {
        String safeSlug = StringUtils.hasText(topicSlug) ? topicSlug.trim() : "general";
        String safeTitle = StringUtils.hasText(title) ? title.trim() : "话题";
        Conversation conversation = ensureConversation(
                TYPE_GROUP,
                "topic-" + safeSlug,
                safeTitle + " 讨论区",
                safeTitle + " 的主题讨论。补充资料和协作信息统一沉淀在这里。",
                LocalDateTime.now()
        );
        ConversationMember member = ensureMember(conversation.getId(), userId);
        member.setPinned(true);
        conversationMemberMapper.updateById(member);
        ensureSeedMessage(conversation.getId(), "topic-host", safeTitle + " 发起",
                "欢迎来到 " + safeTitle + " 的主题讨论区，适合补充说明和协作沟通", LocalDateTime.now());
        return getSummary(userId, conversation.getId());
    }

    @Transactional
    public Map<String, Object> markRead(Long userId, Long conversationId) {
        Conversation conversation = requireConversation(conversationId);
        ConversationMember member = requireMember(conversationId, userId);
        member.setUnreadCount(0);
        member.setLastReadAt(LocalDateTime.now());
        conversationMemberMapper.updateById(member);
        if (TYPE_SYSTEM.equals(conversation.getType())) {
            notificationService.markAllRead(userId);
        }
        return getSummary(userId, conversationId);
    }

    @Transactional
    public Map<String, Object> toggleMute(Long userId, Long conversationId) {
        ConversationMember member = requireMember(conversationId, userId);
        member.setMuted(!Boolean.TRUE.equals(member.getMuted()));
        conversationMemberMapper.updateById(member);
        return getSummary(userId, conversationId);
    }

    @Transactional
    public Map<String, Object> togglePin(Long userId, Long conversationId) {
        ConversationMember member = requireMember(conversationId, userId);
        member.setPinned(!Boolean.TRUE.equals(member.getPinned()));
        conversationMemberMapper.updateById(member);
        return getSummary(userId, conversationId);
    }

    @Transactional
    public Map<String, Object> toggleArchive(Long userId, Long conversationId) {
        ConversationMember member = requireMember(conversationId, userId);
        member.setArchived(!Boolean.TRUE.equals(member.getArchived()));
        conversationMemberMapper.updateById(member);
        return getSummary(userId, conversationId);
    }

    @Transactional
    public Map<String, Object> sendMessage(Long userId, Long conversationId, String content, String senderName) {
        String text = content == null ? "" : content.trim();
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("Message content cannot be empty");
        }

        Conversation conversation = requireConversation(conversationId);
        if (TYPE_PUBLIC.equals(conversation.getType()) || TYPE_SYSTEM.equals(conversation.getType())) {
            throw new IllegalArgumentException("This conversation does not support replies");
        }

        requireMember(conversationId, userId);
        LocalDateTime now = LocalDateTime.now();
        ConversationMessage message = new ConversationMessage();
        message.setConversationId(conversationId);
        message.setSenderId(userId);
        message.setSenderName(StringUtils.hasText(senderName) ? senderName : "我");
        message.setContent(text);
        message.setMessageType(0);
        message.setDeliveryStatus("sent");
        message.setCreatedAt(now);
        conversationMessageMapper.insert(message);

        conversation.setLastMessageAt(now);
        conversation.setUpdatedAt(now);
        conversationMapper.updateById(conversation);

        List<ConversationMember> members = conversationMemberMapper.selectList(new LambdaQueryWrapper<ConversationMember>()
                .eq(ConversationMember::getConversationId, conversationId));
        ConversationMember currentMember = null;
        for (ConversationMember member : members) {
            if (member.getUserId() != null && member.getUserId().equals(userId)) {
                member.setUnreadCount(0);
                member.setLastReadAt(now);
                currentMember = member;
            } else {
                int unreadCount = member.getUnreadCount() == null ? 0 : member.getUnreadCount();
                member.setUnreadCount(unreadCount + 1);
            }
            conversationMemberMapper.updateById(member);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("summary", toSummaryMap(conversation, currentMember == null ? requireMember(conversationId, userId) : currentMember, userId));
        data.put("message", toMessageMap(message));
        return data;
    }

    private void ensureDefaultConversations(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        notificationService.ensureDefaultNotifications(userId);

        Conversation publicConversation = ensureConversation(
                TYPE_PUBLIC,
                "public-lobby",
                "公共频道",
                "公开讨论、群组活动和通知统一入口。",
                now.minusMinutes(12)
        );
        ConversationMember publicMember = ensureMember(publicConversation.getId(), userId);
        if (!Boolean.TRUE.equals(publicMember.getPinned())) {
            publicMember.setPinned(true);
            conversationMemberMapper.updateById(publicMember);
        }
        ensureSeedMessage(publicConversation.getId(), "system", "公共频道",
                "公共频道用于公开讨论、群组活动和通知", now.minusMinutes(12));

        Conversation directConversation = ensureConversation(
                TYPE_DIRECT,
                "seed-direct-" + userId,
                "私信",
                "一对一对话，已读状态和归档等功能控制在这里。",
                now.minusHours(1)
        );
        ensureMember(directConversation.getId(), userId);
        ensureSeedMessage(directConversation.getId(), "peer", "对方账号",
                "私信页面现在可以帮你管理对话，已读状态和归档也在这里了。", now.minusHours(2));

        Conversation groupConversation = ensureConversation(
                TYPE_GROUP,
                "seed-group-" + userId,
                "群组对话",
                "群组负责成员协作，所有话题和任务都可以为多人展开。",
                now.minusHours(3)
        );
        ConversationMember groupMember = ensureMember(groupConversation.getId(), userId);
        if (!Boolean.TRUE.equals(groupMember.getPinned())) {
            groupMember.setPinned(true);
            conversationMemberMapper.updateById(groupMember);
        }
        ensureSeedMessage(groupConversation.getId(), "owner", "群主",
                "这个群组用于项目协作，重要说明和对话都聚集在此。", now.minusHours(4));

        Conversation systemConversation = ensureConversation(
                TYPE_SYSTEM,
                "system-center",
                "系统通知",
                "安全、版本和状态通知统一接收。",
                now.minusMinutes(30)
        );
        ConversationMember systemMember = ensureMember(systemConversation.getId(), userId);
        if (!Boolean.TRUE.equals(systemMember.getMuted())) {
            systemMember.setMuted(true);
            conversationMemberMapper.updateById(systemMember);
        }
        ensureSeedMessage(systemConversation.getId(), "system", "系统",
                "系统通知会集中展示在这里，请留意最新动态", now.minusMinutes(30));
    }

    private Conversation ensureConversation(String type, String bizKey, String title, String description, LocalDateTime fallbackTime) {
        Conversation existing = conversationMapper.selectOne(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getType, type)
                .eq(Conversation::getBizKey, bizKey)
                .last("limit 1"));
        if (existing != null) {
            return existing;
        }

        LocalDateTime now = LocalDateTime.now();
        Conversation conversation = new Conversation();
        conversation.setType(type);
        conversation.setBizKey(bizKey);
        conversation.setTitle(title);
        conversation.setDescription(description);
        conversation.setStatus(1);
        conversation.setLastMessageAt(fallbackTime);
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);
        conversationMapper.insert(conversation);
        return conversation;
    }

    private ConversationMember ensureMember(Long conversationId, Long userId) {
        ConversationMember existing = conversationMemberMapper.selectOne(new LambdaQueryWrapper<ConversationMember>()
                .eq(ConversationMember::getConversationId, conversationId)
                .eq(ConversationMember::getUserId, userId)
                .last("limit 1"));
        if (existing != null) {
            return existing;
        }

        ConversationMember member = new ConversationMember();
        member.setConversationId(conversationId);
        member.setUserId(userId);
        member.setRole("member");
        member.setUnreadCount(0);
        member.setMuted(false);
        member.setPinned(false);
        member.setArchived(false);
        member.setJoinedAt(LocalDateTime.now());
        conversationMemberMapper.insert(member);
        return member;
    }

    private void ensureSeedMessage(Long conversationId, String senderId, String senderName, String content, LocalDateTime createdAt) {
        Long count = conversationMessageMapper.selectCount(new LambdaQueryWrapper<ConversationMessage>()
                .eq(ConversationMessage::getConversationId, conversationId));
        if (count != null && count > 0) {
            return;
        }

        ConversationMessage message = new ConversationMessage();
        message.setConversationId(conversationId);
        message.setSenderId(parseSenderId(senderId));
        message.setSenderName(senderName);
        message.setContent(content);
        message.setMessageType(0);
        message.setDeliveryStatus("read");
        message.setCreatedAt(createdAt);
        conversationMessageMapper.insert(message);
    }

    private Conversation requireConversation(Long conversationId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || conversation.getStatus() == null || conversation.getStatus() != 1) {
            throw new IllegalArgumentException("Conversation not found");
        }
        return conversation;
    }

    private ConversationMember requireMember(Long conversationId, Long userId) {
        ConversationMember member = conversationMemberMapper.selectOne(new LambdaQueryWrapper<ConversationMember>()
                .eq(ConversationMember::getConversationId, conversationId)
                .eq(ConversationMember::getUserId, userId)
                .last("limit 1"));
        if (member == null) {
            throw new IllegalArgumentException("Conversation not found");
        }
        return member;
    }

    private Map<String, Object> toSummaryMap(Conversation conversation, ConversationMember member, Long userId) {
        String title = conversation.getTitle();
        String preview = latestPreview(conversation);
        int unreadCount = member.getUnreadCount() == null ? 0 : member.getUnreadCount();
        String description = conversation.getDescription() == null ? "" : conversation.getDescription();
        String peerUserId = "";
        int memberCount = countMembers(conversation.getId());

        if (TYPE_DIRECT.equals(conversation.getType())) {
            title = resolveDirectTitle(conversation, userId);
            description = "和 " + title + " 的一对一对话，已读、静音和归档都在这里闭环。";
            peerUserId = resolveDirectPeerUserId(conversation, userId);
        } else if (TYPE_SYSTEM.equals(conversation.getType())) {
            title = "系统通知";
            preview = notificationService.latestPreview(userId);
            unreadCount = notificationService.countUnread(userId);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", conversation.getId().toString());
        data.put("kind", toRouteSegment(conversation.getType()));
        data.put("title", title);
        data.put("preview", preview);
        data.put("updatedAt", resolveUpdatedAt(conversation));
        data.put("unreadCount", unreadCount);
        data.put("isAvailable", true);
        data.put("description", description);
        data.put("canSend", TYPE_DIRECT.equals(conversation.getType()) || TYPE_GROUP.equals(conversation.getType()));
        data.put("isPinned", Boolean.TRUE.equals(member.getPinned()));
        data.put("isMuted", Boolean.TRUE.equals(member.getMuted()));
        data.put("isArchived", Boolean.TRUE.equals(member.getArchived()));
        data.put("peerUserId", peerUserId);
        data.put("memberCount", memberCount);
        return data;
    }

    private String buildDirectBizKey(Long userId, String targetUserId) {
        Long targetId = parseLongOrNull(targetUserId);
        if (targetId != null) {
            long left = Math.min(userId, targetId);
            long right = Math.max(userId, targetId);
            return "direct-" + left + "-" + right;
        }

        String currentUserId = String.valueOf(userId);
        if (currentUserId.compareTo(targetUserId) <= 0) {
            return "direct-" + currentUserId + "-" + targetUserId;
        }
        return "direct-" + targetUserId + "-" + currentUserId;
    }

    private String buildDirectMetadata(Long userId, String currentUsername, String targetUserId, String targetUsername) {
        return userId + "=" + sanitizeDirectName(currentUsername) + ";"
                + targetUserId + "=" + sanitizeDirectName(targetUsername);
    }

    private String resolveDirectPeerUserId(Conversation conversation, Long userId) {
        String rawTitle = conversation.getTitle();
        if (!StringUtils.hasText(rawTitle) || !rawTitle.contains("=")) {
            return "";
        }

        for (String segment : rawTitle.split(";")) {
            int index = segment.indexOf('=');
            if (index < 0) {
                continue;
            }
            String memberId = segment.substring(0, index).trim();
            if (!String.valueOf(userId).equals(memberId)) {
                return memberId;
            }
        }
        return "";
    }

    private String resolveDirectTitle(Conversation conversation, Long userId) {
        String rawTitle = conversation.getTitle();
        if (!StringUtils.hasText(rawTitle) || !rawTitle.contains("=")) {
            return StringUtils.hasText(rawTitle) ? rawTitle : "私信";
        }

        for (String segment : rawTitle.split(";")) {
            int index = segment.indexOf('=');
            if (index < 0) {
                continue;
            }
            String memberId = segment.substring(0, index).trim();
            String memberName = segment.substring(index + 1).trim();
            if (!String.valueOf(userId).equals(memberId) && StringUtils.hasText(memberName)) {
                return memberName;
            }
        }
        return "私信";
    }

    private String sanitizeDirectName(String value) {
        if (!StringUtils.hasText(value)) {
            return "用户";
        }
        return value.replace(';', ' ').replace('=', ' ').trim();
    }

    private int countMembers(Long conversationId) {
        Long count = conversationMemberMapper.selectCount(new LambdaQueryWrapper<ConversationMember>()
                .eq(ConversationMember::getConversationId, conversationId));
        return count == null ? 0 : count.intValue();
    }

    private Map<String, Object> toMessageMap(ConversationMessage message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", message.getId() == null ? "" : message.getId().toString());
        data.put("senderId", message.getSenderId() == null ? "" : message.getSenderId().toString());
        data.put("senderName", message.getSenderName());
        data.put("content", message.getContent());
        data.put("createTime", message.getCreatedAt() == null ? "" : message.getCreatedAt().toString());
        data.put("type", message.getMessageType() == null ? 0 : message.getMessageType());
        data.put("deliveryStatus", message.getDeliveryStatus() == null ? "sent" : message.getDeliveryStatus());
        return data;
    }

    private String latestPreview(Conversation conversation) {
        List<ConversationMessage> messages = conversationMessageMapper.selectList(new LambdaQueryWrapper<ConversationMessage>()
                .eq(ConversationMessage::getConversationId, conversation.getId())
                .orderByDesc(ConversationMessage::getCreatedAt)
                .orderByDesc(ConversationMessage::getId)
                .last("limit 1"));
        if (messages.isEmpty()) {
            return conversation.getDescription() == null ? "" : conversation.getDescription();
        }
        return messages.get(0).getContent();
    }

    private String resolveUpdatedAt(Conversation conversation) {
        LocalDateTime time = conversation.getLastMessageAt();
        if (time == null) {
            time = conversation.getUpdatedAt();
        }
        if (time == null) {
            time = conversation.getCreatedAt();
        }
        return time == null ? "" : time.toString();
    }

    private String toRouteSegment(String type) {
        if (TYPE_PUBLIC.equals(type)) {
            return "public";
        }
        if (TYPE_DIRECT.equals(type)) {
            return "direct";
        }
        if (TYPE_GROUP.equals(type)) {
            return "group";
        }
        return "system";
    }

    private Long parseLongOrNull(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Long parseSenderId(String senderId) {
        try {
            return Long.parseLong(senderId);
        } catch (Exception ignored) {
            return 0L;
        }
    }
}