package com.interstellar.chat.domain.room;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interstellar.chat.domain.conversation.*;
import com.interstellar.chat.domain.message.ConversationMessage;
import com.interstellar.chat.domain.message.ConversationMessageMapper;
import com.interstellar.common.util.SanitizeUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ChatRoomService {

    private static final String TYPE_GROUP = "group";

    @Autowired
    private ChatRoomMapper chatRoomMapper;
    @Autowired
    private ConversationMapper conversationMapper;
    @Autowired
    private ConversationMemberMapper conversationMemberMapper;
    @Autowired
    private ConversationMessageMapper conversationMessageMapper;
    @Autowired
    private JoinRequestMapper joinRequestMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ==================== 列表 ====================

    public List<Map<String, Object>> listRooms(Long userId, String planet, boolean clubsOnly,
                                                String keyword, String sort) {
        LambdaQueryWrapper<ChatRoom> wrapper = new LambdaQueryWrapper<ChatRoom>()
                .eq(ChatRoom::getStatus, 1);

        if (StringUtils.hasText(planet)) {
            wrapper.eq(ChatRoom::getPlanet, planet);
        }
        // 俱乐部模式：只显示可发现的（有 planet 的）
        if (clubsOnly) {
            wrapper.isNotNull(ChatRoom::getPlanet)
                   .ne(ChatRoom::getPlanet, "")
                   .eq(ChatRoom::getDiscoverable, 1);
        }
        // 关键词搜索
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(ChatRoom::getName, keyword)
                    .or()
                    .like(ChatRoom::getDescription, keyword));
        }
        // 排序
        String safeSort = sort == null ? "newest" : sort;
        switch (safeSort) {
            case "members" -> wrapper.orderByDesc(ChatRoom::getMemberCount);
            case "active" -> wrapper.orderByDesc(ChatRoom::getUpdatedAt);
            default -> wrapper.orderByDesc(ChatRoom::getCreatedAt)
                              .orderByDesc(ChatRoom::getId);
        }

        List<ChatRoom> rooms = chatRoomMapper.selectList(wrapper);

        return rooms.stream().map(room -> {
            boolean joined = isMember(room.getConversationId(), userId);
            String userRole = getUserRole(room.getConversationId(), userId);
            // 私密房间非成员不可见
            if ("private".equals(room.getType()) && !joined) return null;
            return toRoomMap(room, joined, userRole);
        }).filter(Objects::nonNull).toList();
    }

    // ==================== 创建 ====================

    @Transactional
    public Map<String, Object> createRoom(Long userId, String username, String name,
                                          String description, String icon, String type,
                                          String planet, Integer discoverable, String joinMode,
                                          String joinQuestion) {
        String safeName = SanitizeUtil.stripHtml(StringUtils.hasText(name) ? name.trim() : "聊天室");
        String safeDesc = SanitizeUtil.stripHtml(StringUtils.hasText(description) ? description.trim() : "欢迎加入");
        String safeIcon = StringUtils.hasText(icon) ? icon.trim() : "";
        String safeType = "private".equals(type) ? "private" : "public";
        String safePlanet = StringUtils.hasText(planet) ? planet.trim() : null;
        String safeJoinMode = ("approval".equals(joinMode) || "closed".equals(joinMode)) ? joinMode : "open";

        // 一人一俱乐部约束（仅俱乐部，普通聊天室不受限制）
        if (safePlanet != null) {
            Long existingClubId = findUserClubId(userId);
            if (existingClubId != null) {
                throw new IllegalArgumentException("你已加入其他俱乐部，请先退出后再创建");
            }
        }
        int safeDiscoverable = (discoverable != null && discoverable == 0) ? 0 : 1;
        LocalDateTime now = LocalDateTime.now();

        // 1. Create conversation
        Conversation conversation = new Conversation();
        conversation.setType(TYPE_GROUP);
        conversation.setBizKey("room-pending");
        conversation.setTitle(safeName);
        conversation.setDescription(safeDesc);
        conversation.setStatus(1);
        conversation.setLastMessageAt(now);
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);
        conversationMapper.insert(conversation);

        conversation.setBizKey("room-" + conversation.getId());
        conversationMapper.updateById(conversation);

        // 2. Create room record
        ChatRoom room = new ChatRoom();
        room.setConversationId(conversation.getId());
        room.setCreatorId(userId);
        room.setCreatorName(StringUtils.hasText(username) ? username : "用户");
        room.setName(safeName);
        room.setDescription(safeDesc);
        room.setIcon(safeIcon);
        room.setType(safeType);
        room.setPlanet(safePlanet);
        room.setDiscoverable(safeDiscoverable);
        room.setJoinMode(safeJoinMode);
        room.setJoinQuestion(StringUtils.hasText(joinQuestion) ? SanitizeUtil.stripHtml(joinQuestion.trim()) : "");
        room.setMaxMembers(500);
        room.setMemberCount(1);
        room.setStatus(1);
        room.setCreatedAt(now);
        room.setUpdatedAt(now);
        chatRoomMapper.insert(room);

        // 3. Add creator as owner
        ConversationMember member = new ConversationMember();
        member.setConversationId(conversation.getId());
        member.setUserId(userId);
        member.setRole("owner");
        member.setNickname(StringUtils.hasText(username) ? username : "");
        member.setUnreadCount(0);
        member.setMuted(false);
        member.setPinned(false);
        member.setArchived(false);
        member.setJoinedAt(now);
        conversationMemberMapper.insert(member);

        // 4. Seed welcome message
        ConversationMessage seed = new ConversationMessage();
        seed.setConversationId(conversation.getId());
        seed.setSenderId(0L);
        seed.setSenderName("系统");
        seed.setContent("欢迎来到「" + safeName + "」");
        seed.setMessageType(0);
        seed.setDeliveryStatus("sent");
        seed.setCreatedAt(now);
        conversationMessageMapper.insert(seed);

        return toRoomMap(room, true, "owner");
    }

    // ==================== 加入 ====================

    @Transactional
    public Map<String, Object> joinRoom(Long userId, Long roomId, String username, String answer) {
        ChatRoom room = requireRoom(roomId);
        if (isMember(room.getConversationId(), userId)) {
            return toRoomMap(room, true, getUserRole(room.getConversationId(), userId));
        }

        // 一人一俱乐部约束
        Long existingClubId = findUserClubId(userId);
        if (existingClubId != null) {
            throw new IllegalArgumentException("你已加入其他俱乐部，请先退出后再加入");
        }

        String joinMode = room.getJoinMode() == null ? "open" : room.getJoinMode();
        if ("closed".equals(joinMode)) {
            throw new IllegalArgumentException("该俱乐部暂不接受新成员");
        }
        if ("approval".equals(joinMode)) {
            // 创建申请记录
            Long existing = joinRequestMapper.selectCount(
                    new LambdaQueryWrapper<JoinRequest>()
                            .eq(JoinRequest::getRoomId, roomId)
                            .eq(JoinRequest::getUserId, userId)
                            .eq(JoinRequest::getStatus, "pending"));
            if (existing > 0) throw new IllegalArgumentException("已提交申请，请等待审批");
            JoinRequest req = new JoinRequest();
            req.setRoomId(roomId);
            req.setUserId(userId);
            req.setStatus("pending");
            req.setAnswer(answer != null ? SanitizeUtil.stripHtml(answer.trim()) : "");
            req.setCreatedAt(LocalDateTime.now());
            joinRequestMapper.insert(req);
            throw new IllegalArgumentException("已提交加入申请，请等待审批");
        }

        // open mode - direct join
        return doJoin(room, userId, "member", username);
    }

    @Transactional
    public Map<String, Object> approveJoin(Long operatorId, Long roomId, Long targetUserId) {
        ChatRoom room = requireRoom(roomId);
        requireRole(room, operatorId, "owner", "admin");

        // 一人一俱乐部约束
        Long existingClubId = findUserClubId(targetUserId);
        if (existingClubId != null) {
            throw new IllegalArgumentException("该用户已加入其他俱乐部");
        }

        JoinRequest req = joinRequestMapper.selectOne(
                new LambdaQueryWrapper<JoinRequest>()
                        .eq(JoinRequest::getRoomId, roomId)
                        .eq(JoinRequest::getUserId, targetUserId)
                        .eq(JoinRequest::getStatus, "pending"));
        if (req == null) throw new IllegalArgumentException("申请不存在");
        req.setStatus("approved");
        joinRequestMapper.updateById(req);

        return doJoin(room, targetUserId, "member", null);
    }

    @Transactional
    public void rejectJoin(Long operatorId, Long roomId, Long targetUserId) {
        ChatRoom room = requireRoom(roomId);
        requireRole(room, operatorId, "owner", "admin");

        JoinRequest req = joinRequestMapper.selectOne(
                new LambdaQueryWrapper<JoinRequest>()
                        .eq(JoinRequest::getRoomId, roomId)
                        .eq(JoinRequest::getUserId, targetUserId)
                        .eq(JoinRequest::getStatus, "pending"));
        if (req == null) throw new IllegalArgumentException("申请不存在");
        req.setStatus("rejected");
        joinRequestMapper.updateById(req);
    }

    public List<Map<String, Object>> listJoinRequests(Long roomId) {
        List<JoinRequest> requests = joinRequestMapper.selectList(
                new LambdaQueryWrapper<JoinRequest>()
                        .eq(JoinRequest::getRoomId, roomId)
                        .eq(JoinRequest::getStatus, "pending")
                        .orderByDesc(JoinRequest::getCreatedAt));
        return requests.stream().map(r -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("userId", r.getUserId().toString());
            data.put("status", r.getStatus());
            data.put("answer", r.getAnswer() == null ? "" : r.getAnswer());
            data.put("createdAt", r.getCreatedAt() == null ? "" : r.getCreatedAt().toString());
            return data;
        }).toList();
    }

    @Transactional
    public void inviteUser(Long operatorId, Long roomId, Long targetUserId) {
        ChatRoom room = requireRoom(roomId);
        requireRole(room, operatorId, "owner", "admin");

        String joinMode = room.getJoinMode() == null ? "open" : room.getJoinMode();
        if ("closed".equals(joinMode)) {
            throw new IllegalArgumentException("该俱乐部已关闭，无法邀请");
        }
        if (isMember(room.getConversationId(), targetUserId)) {
            throw new IllegalArgumentException("用户已是成员");
        }
        doJoin(room, targetUserId, "member", null);
    }

    // ==================== 退出 ====================

    @Transactional
    public Map<String, Object> leaveRoom(Long userId, Long roomId) {
        ChatRoom room = requireRoom(roomId);
        ConversationMember member = requireMember(room.getConversationId(), userId);
        if ("owner".equals(member.getRole())) {
            throw new IllegalArgumentException("部长不能退出，请先转让部长");
        }
        conversationMemberMapper.delete(new LambdaQueryWrapper<ConversationMember>()
                .eq(ConversationMember::getConversationId, room.getConversationId())
                .eq(ConversationMember::getUserId, userId));
        if (room.getMemberCount() != null && room.getMemberCount() > 0) {
            room.setMemberCount(room.getMemberCount() - 1);
        }
        room.setUpdatedAt(LocalDateTime.now());
        chatRoomMapper.updateById(room);
        return toRoomMap(room, false, null);
    }

    // ==================== 详情 ====================

    public Map<String, Object> getRoom(Long userId, Long roomId) {
        ChatRoom room = requireRoom(roomId);
        boolean joined = room.getConversationId() != null && isMember(room.getConversationId(), userId);
        String userRole = joined ? getUserRole(room.getConversationId(), userId) : null;
        return toRoomMap(room, joined, userRole);
    }

    public boolean isMemberByRoom(Long roomId, Long userId) {
        ChatRoom room = chatRoomMapper.selectById(roomId);
        if (room == null || room.getConversationId() == null) return false;
        return isMember(room.getConversationId(), userId);
    }

    public String getUserClubName(Long userId) {
        // 查找用户所属的所有俱乐部（不再仅限 owner），优先级 owner > admin > member
        List<ConversationMember> memberships = conversationMemberMapper.selectList(
                new LambdaQueryWrapper<ConversationMember>()
                        .eq(ConversationMember::getUserId, userId));
        // 按角色优先级排序
        Map<String, Integer> rolePriority = Map.of("owner", 0, "admin", 1, "member", 2);
        memberships.sort(Comparator.comparingInt(m ->
                rolePriority.getOrDefault(m.getRole(), 99)));

        for (ConversationMember m : memberships) {
            ChatRoom room = chatRoomMapper.selectOne(
                    new LambdaQueryWrapper<ChatRoom>()
                            .eq(ChatRoom::getConversationId, m.getConversationId())
                            .eq(ChatRoom::getStatus, 1)
                            .isNotNull(ChatRoom::getPlanet)
                            .ne(ChatRoom::getPlanet, ""));
            if (room != null) return room.getName();
        }
        return "";
    }

    // ==================== 编辑 ====================

    @Transactional
    public Map<String, Object> updateRoom(Long userId, Long roomId, String name, String description,
                                          String icon, String type, String planet,
                                          Integer discoverable, String joinMode, String joinQuestion) {
        ChatRoom room = requireRoom(roomId);
        requireRole(room, userId, "owner", "admin");

        if (StringUtils.hasText(name)) room.setName(SanitizeUtil.stripHtml(name.trim()));
        if (description != null) room.setDescription(SanitizeUtil.stripHtml(description.trim()));
        if (icon != null) room.setIcon(icon.trim());
        if (StringUtils.hasText(type)) room.setType("private".equals(type) ? "private" : "public");
        if (planet != null) room.setPlanet(StringUtils.hasText(planet) ? planet.trim() : null);
        if (discoverable != null) room.setDiscoverable(discoverable == 0 ? 0 : 1);
        if (StringUtils.hasText(joinMode)) room.setJoinMode(joinMode);
        if (joinQuestion != null) room.setJoinQuestion(SanitizeUtil.stripHtml(joinQuestion.trim()));
        room.setUpdatedAt(LocalDateTime.now());
        chatRoomMapper.updateById(room);

        return toRoomMap(room, true, getUserRole(room.getConversationId(), userId));
    }

    // ==================== 成员管理 ====================

    public List<Map<String, Object>> listMembers(Long roomId) {
        ChatRoom room = requireRoom(roomId);
        List<ConversationMember> members = conversationMemberMapper.selectList(
                new LambdaQueryWrapper<ConversationMember>()
                        .eq(ConversationMember::getConversationId, room.getConversationId())
                        .orderByDesc(ConversationMember::getRole)
                        .orderByAsc(ConversationMember::getJoinedAt));
        return members.stream().map(m -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("userId", m.getUserId().toString());
            data.put("role", m.getRole());
            data.put("nickname", m.getNickname() == null ? "" : m.getNickname());
            data.put("avatarUrl", m.getAvatarUrl() == null ? "" : m.getAvatarUrl());
            data.put("muted", m.getMuted() != null && m.getMuted());
            data.put("joinedAt", m.getJoinedAt() == null ? "" : m.getJoinedAt().toString());
            return data;
        }).toList();
    }

    @Transactional
    public void promoteMember(Long userId, Long roomId, Long targetUserId) {
        ChatRoom room = requireRoom(roomId);
        requireRole(room, userId, "owner");
        ConversationMember target = requireMember(room.getConversationId(), targetUserId);
        if ("owner".equals(target.getRole())) throw new IllegalArgumentException("不能操作部长");
        target.setRole("admin");
        conversationMemberMapper.updateById(target);
    }

    @Transactional
    public void demoteMember(Long userId, Long roomId, Long targetUserId) {
        ChatRoom room = requireRoom(roomId);
        requireRole(room, userId, "owner");
        ConversationMember target = requireMember(room.getConversationId(), targetUserId);
        if ("owner".equals(target.getRole())) throw new IllegalArgumentException("不能操作部长");
        target.setRole("member");
        conversationMemberMapper.updateById(target);
    }

    @Transactional
    public void kickMember(Long operatorId, Long roomId, Long targetUserId) {
        ChatRoom room = requireRoom(roomId);
        requireRole(room, operatorId, "owner", "admin");
        ConversationMember target = requireMember(room.getConversationId(), targetUserId);
        if ("owner".equals(target.getRole())) throw new IllegalArgumentException("不能踢出部长");
        // admin 不能踢 admin
        String operatorRole = getUserRole(room.getConversationId(), operatorId);
        if ("admin".equals(operatorRole) && "admin".equals(target.getRole())) {
            throw new IllegalArgumentException("副部长不能踢出副部长");
        }
        conversationMemberMapper.delete(new LambdaQueryWrapper<ConversationMember>()
                .eq(ConversationMember::getConversationId, room.getConversationId())
                .eq(ConversationMember::getUserId, targetUserId));
        if (room.getMemberCount() != null && room.getMemberCount() > 0) {
            room.setMemberCount(room.getMemberCount() - 1);
        }
        room.setUpdatedAt(LocalDateTime.now());
        chatRoomMapper.updateById(room);
    }

    @Transactional
    public void banMember(Long operatorId, Long roomId, Long targetUserId) {
        ChatRoom room = requireRoom(roomId);
        requireRole(room, operatorId, "owner", "admin");
        ConversationMember target = requireMember(room.getConversationId(), targetUserId);
        if ("owner".equals(target.getRole())) throw new IllegalArgumentException("不能禁言部长");
        target.setMuted(true);
        conversationMemberMapper.updateById(target);
    }

    @Transactional
    public void unbanMember(Long operatorId, Long roomId, Long targetUserId) {
        ChatRoom room = requireRoom(roomId);
        requireRole(room, operatorId, "owner", "admin");
        ConversationMember target = requireMember(room.getConversationId(), targetUserId);
        target.setMuted(false);
        conversationMemberMapper.updateById(target);
    }

    @Transactional
    public void transferPresident(Long operatorId, Long roomId, Long targetUserId) {
        ChatRoom room = requireRoom(roomId);
        ConversationMember operator = requireMember(room.getConversationId(), operatorId);
        if (!"owner".equals(operator.getRole())) throw new IllegalArgumentException("只有部长可以转让");
        ConversationMember target = requireMember(room.getConversationId(), targetUserId);
        operator.setRole("member");
        target.setRole("owner");
        conversationMemberMapper.updateById(operator);
        conversationMemberMapper.updateById(target);
    }

    @Transactional
    public void deleteClub(Long operatorId, Long roomId) {
        ChatRoom room = requireRoom(roomId);
        requireRole(room, operatorId, "owner");
        room.setStatus(0);
        room.setUpdatedAt(LocalDateTime.now());
        chatRoomMapper.updateById(room);
    }

    // ==================== Helpers ====================

    private Map<String, Object> doJoin(ChatRoom room, Long userId, String role, String username) {
        if (room.getMaxMembers() != null && room.getMemberCount() != null
                && room.getMemberCount() >= room.getMaxMembers()) {
            throw new IllegalArgumentException("成员已满");
        }
        ConversationMember member = new ConversationMember();
        member.setConversationId(room.getConversationId());
        member.setUserId(userId);
        member.setRole(role);
        member.setNickname(StringUtils.hasText(username) ? username : "");
        member.setUnreadCount(0);
        member.setMuted(false);
        member.setPinned(false);
        member.setArchived(false);
        member.setJoinedAt(LocalDateTime.now());
        conversationMemberMapper.insert(member);

        room.setMemberCount(room.getMemberCount() == null ? 1 : room.getMemberCount() + 1);
        room.setUpdatedAt(LocalDateTime.now());
        chatRoomMapper.updateById(room);

        return toRoomMap(room, true, role);
    }

    private boolean isMember(Long conversationId, Long userId) {
        if (conversationId == null) return false;
        Long count = conversationMemberMapper.selectCount(
                new LambdaQueryWrapper<ConversationMember>()
                        .eq(ConversationMember::getConversationId, conversationId)
                        .eq(ConversationMember::getUserId, userId));
        return count != null && count > 0;
    }

    /**
     * 查找用户已加入的俱乐部 roomId，用于一人一俱乐部约束。
     * 返回 null 表示用户未加入任何俱乐部。
     */
    private Long findUserClubId(Long userId) {
        List<ConversationMember> memberships = conversationMemberMapper.selectList(
                new LambdaQueryWrapper<ConversationMember>()
                        .eq(ConversationMember::getUserId, userId));
        for (ConversationMember m : memberships) {
            ChatRoom room = chatRoomMapper.selectOne(
                    new LambdaQueryWrapper<ChatRoom>()
                            .eq(ChatRoom::getConversationId, m.getConversationId())
                            .eq(ChatRoom::getStatus, 1)
                            .isNotNull(ChatRoom::getPlanet)
                            .ne(ChatRoom::getPlanet, ""));
            if (room != null) return room.getId();
        }
        return null;
    }

    private String getUserRole(Long conversationId, Long userId) {
        if (conversationId == null) return null;
        ConversationMember member = conversationMemberMapper.selectOne(
                new LambdaQueryWrapper<ConversationMember>()
                        .eq(ConversationMember::getConversationId, conversationId)
                        .eq(ConversationMember::getUserId, userId));
        return member == null ? null : member.getRole();
    }

    private ChatRoom requireRoom(Long roomId) {
        ChatRoom room = chatRoomMapper.selectById(roomId);
        if (room == null || room.getStatus() == null || room.getStatus() != 1) {
            throw new IllegalArgumentException("俱乐部不存在");
        }
        return room;
    }

    private ConversationMember requireMember(Long conversationId, Long userId) {
        ConversationMember member = conversationMemberMapper.selectOne(
                new LambdaQueryWrapper<ConversationMember>()
                        .eq(ConversationMember::getConversationId, conversationId)
                        .eq(ConversationMember::getUserId, userId));
        if (member == null) throw new IllegalArgumentException("用户不是成员");
        return member;
    }

    private void requireRole(ChatRoom room, Long userId, String... allowedRoles) {
        ConversationMember member = requireMember(room.getConversationId(), userId);
        for (String role : allowedRoles) {
            if (role.equals(member.getRole())) return;
        }
        throw new IllegalArgumentException("权限不足");
    }

    private Map<String, Object> toRoomMap(ChatRoom room, boolean joined, String userRole) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", room.getId().toString());
        data.put("conversationId", room.getConversationId() == null ? "" : room.getConversationId().toString());
        data.put("name", room.getName());
        data.put("description", room.getDescription() == null ? "" : room.getDescription());
        data.put("icon", room.getIcon() == null ? "" : room.getIcon());
        data.put("type", room.getType() == null ? "public" : room.getType());
        data.put("planet", room.getPlanet() == null ? "" : room.getPlanet());
        data.put("discoverable", room.getDiscoverable() == null ? 1 : room.getDiscoverable());
        data.put("joinMode", room.getJoinMode() == null ? "open" : room.getJoinMode());
        data.put("joinQuestion", room.getJoinQuestion() == null ? "" : room.getJoinQuestion());
        data.put("creatorId", room.getCreatorId() == null ? "" : room.getCreatorId().toString());
        data.put("creatorName", room.getCreatorName() == null ? "" : room.getCreatorName());
        data.put("memberCount", room.getMemberCount() == null ? 0 : room.getMemberCount());
        data.put("maxMembers", room.getMaxMembers() == null ? 500 : room.getMaxMembers());
        data.put("joined", joined);
        data.put("role", userRole == null ? "" : userRole);
        data.put("announcement", room.getAnnouncement() == null ? "" : room.getAnnouncement());
        data.put("pinnedMessageId", room.getPinnedMessageId() == null ? "" : room.getPinnedMessageId().toString());
        data.put("createdAt", room.getCreatedAt() == null ? "" : room.getCreatedAt().toString());
        data.put("updatedAt", room.getUpdatedAt() == null ? "" : room.getUpdatedAt().toString());
        return data;
    }

    // ==================== 群公告 ====================

    @Transactional
    public void setAnnouncement(Long userId, Long roomId, String announcement) {
        ChatRoom room = requireRoom(roomId);
        requireRole(room, userId, "owner", "admin");
        room.setAnnouncement(SanitizeUtil.stripHtml(announcement));
        room.setUpdatedAt(LocalDateTime.now());
        chatRoomMapper.updateById(room);

        // 发送系统消息通知公告变更
        ConversationMessage sysMsg = new ConversationMessage();
        sysMsg.setConversationId(room.getConversationId());
        sysMsg.setSenderId(0L);
        sysMsg.setSenderName("系统");
        sysMsg.setContent("群公告已更新：" + room.getAnnouncement());
        sysMsg.setMessageType(0);
        sysMsg.setDeliveryStatus("sent");
        sysMsg.setCreatedAt(LocalDateTime.now());
        conversationMessageMapper.insert(sysMsg);
    }

    public void clearAnnouncement(Long userId, Long roomId) {
        ChatRoom room = requireRoom(roomId);
        requireRole(room, userId, "owner", "admin");
        room.setAnnouncement(null);
        room.setUpdatedAt(LocalDateTime.now());
        chatRoomMapper.updateById(room);
    }

    // ==================== 置顶消息 ====================

    @Transactional
    public void pinMessage(Long userId, Long roomId, Long messageId) {
        ChatRoom room = requireRoom(roomId);
        requireRole(room, userId, "owner", "admin");

        // 取消之前的置顶
        if (room.getPinnedMessageId() != null) {
            jdbcTemplate.update("UPDATE conversation_message SET is_pinned = 0 WHERE id = ?",
                    room.getPinnedMessageId());
        }

        room.setPinnedMessageId(messageId);
        room.setUpdatedAt(LocalDateTime.now());
        chatRoomMapper.updateById(room);

        jdbcTemplate.update("UPDATE conversation_message SET is_pinned = 1 WHERE id = ?", messageId);
    }

    public void unpinMessage(Long userId, Long roomId) {
        ChatRoom room = requireRoom(roomId);
        requireRole(room, userId, "owner", "admin");

        if (room.getPinnedMessageId() != null) {
            jdbcTemplate.update("UPDATE conversation_message SET is_pinned = 0 WHERE id = ?",
                    room.getPinnedMessageId());
        }
        room.setPinnedMessageId(null);
        room.setUpdatedAt(LocalDateTime.now());
        chatRoomMapper.updateById(room);
    }

    public Map<String, Object> getPinnedMessage(Long roomId) {
        ChatRoom room = requireRoom(roomId);
        if (room.getPinnedMessageId() == null) return null;

        List<Map<String, Object>> msgs = jdbcTemplate.queryForList(
                "SELECT id, sender_id, sender_name, content, message_type, created_at " +
                "FROM conversation_message WHERE id = ?", room.getPinnedMessageId());
        return msgs.isEmpty() ? null : msgs.get(0);
    }
}
