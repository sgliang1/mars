package com.mars.chat.domain.room;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mars.chat.domain.conversation.*;
import com.mars.chat.domain.message.ConversationMessage;
import com.mars.chat.domain.message.ConversationMessageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    public List<Map<String, Object>> listRooms(Long userId) {
        List<ChatRoom> rooms = chatRoomMapper.selectList(
                new LambdaQueryWrapper<ChatRoom>()
                        .eq(ChatRoom::getStatus, 1)
                        .orderByDesc(ChatRoom::getUpdatedAt)
                        .orderByDesc(ChatRoom::getId));

        return rooms.stream().map(room -> {
            boolean joined = isMember(room.getConversationId(), userId);
            return toRoomMap(room, joined);
        }).toList();
    }

    @Transactional
    public Map<String, Object> createRoom(Long userId, String username, String name, String description, String icon) {
        String safeName = StringUtils.hasText(name) ? name.trim() : "聊天室";
        String safeDesc = StringUtils.hasText(description) ? description.trim() : "欢迎加入聊天";
        String safeIcon = StringUtils.hasText(icon) ? icon.trim() : "";
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

        // 2. Update bizKey with real id
        conversation.setBizKey("room-" + conversation.getId());
        conversationMapper.updateById(conversation);

        // 3. Create room record
        ChatRoom room = new ChatRoom();
        room.setConversationId(conversation.getId());
        room.setCreatorId(userId);
        room.setCreatorName(StringUtils.hasText(username) ? username : "用户");
        room.setName(safeName);
        room.setDescription(safeDesc);
        room.setIcon(safeIcon);
        room.setMaxMembers(500);
        room.setMemberCount(1);
        room.setStatus(1);
        room.setCreatedAt(now);
        room.setUpdatedAt(now);
        chatRoomMapper.insert(room);

        // 4. Add creator as member
        ConversationMember member = new ConversationMember();
        member.setConversationId(conversation.getId());
        member.setUserId(userId);
        member.setRole("owner");
        member.setUnreadCount(0);
        member.setMuted(false);
        member.setPinned(false);
        member.setArchived(false);
        member.setJoinedAt(now);
        conversationMemberMapper.insert(member);

        // 5. Seed welcome message
        ConversationMessage seed = new ConversationMessage();
        seed.setConversationId(conversation.getId());
        seed.setSenderId(0L);
        seed.setSenderName("系统");
        seed.setContent("欢迎来到「" + safeName + "」，开始聊天吧！");
        seed.setMessageType(0);
        seed.setDeliveryStatus("sent");
        seed.setCreatedAt(now);
        conversationMessageMapper.insert(seed);

        return toRoomMap(room, true);
    }

    @Transactional
    public Map<String, Object> joinRoom(Long userId, Long roomId) {
        ChatRoom room = requireRoom(roomId);
        if (room.getConversationId() == null) {
            throw new IllegalArgumentException("房间数据异常");
        }

        if (isMember(room.getConversationId(), userId)) {
            return toRoomMap(room, true);
        }

        if (room.getMaxMembers() != null && room.getMemberCount() != null
                && room.getMemberCount() >= room.getMaxMembers()) {
            throw new IllegalArgumentException("房间已满");
        }

        ConversationMember member = new ConversationMember();
        member.setConversationId(room.getConversationId());
        member.setUserId(userId);
        member.setRole("member");
        member.setUnreadCount(0);
        member.setMuted(false);
        member.setPinned(false);
        member.setArchived(false);
        member.setJoinedAt(LocalDateTime.now());
        conversationMemberMapper.insert(member);

        room.setMemberCount(room.getMemberCount() == null ? 1 : room.getMemberCount() + 1);
        room.setUpdatedAt(LocalDateTime.now());
        chatRoomMapper.updateById(room);

        return toRoomMap(room, true);
    }

    @Transactional
    public Map<String, Object> leaveRoom(Long userId, Long roomId) {
        ChatRoom room = requireRoom(roomId);
        if (room.getConversationId() == null) {
            throw new IllegalArgumentException("房间数据异常");
        }

        conversationMemberMapper.delete(new LambdaQueryWrapper<ConversationMember>()
                .eq(ConversationMember::getConversationId, room.getConversationId())
                .eq(ConversationMember::getUserId, userId));

        if (room.getMemberCount() != null && room.getMemberCount() > 0) {
            room.setMemberCount(room.getMemberCount() - 1);
        }
        room.setUpdatedAt(LocalDateTime.now());
        chatRoomMapper.updateById(room);

        return toRoomMap(room, false);
    }

    public Map<String, Object> getRoom(Long userId, Long roomId) {
        ChatRoom room = requireRoom(roomId);
        boolean joined = room.getConversationId() != null && isMember(room.getConversationId(), userId);
        return toRoomMap(room, joined);
    }

    private boolean isMember(Long conversationId, Long userId) {
        if (conversationId == null) return false;
        Long count = conversationMemberMapper.selectCount(
                new LambdaQueryWrapper<ConversationMember>()
                        .eq(ConversationMember::getConversationId, conversationId)
                        .eq(ConversationMember::getUserId, userId));
        return count != null && count > 0;
    }

    private ChatRoom requireRoom(Long roomId) {
        ChatRoom room = chatRoomMapper.selectById(roomId);
        if (room == null || room.getStatus() == null || room.getStatus() != 1) {
            throw new IllegalArgumentException("房间不存在");
        }
        return room;
    }

    private Map<String, Object> toRoomMap(ChatRoom room, boolean joined) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", room.getId().toString());
        data.put("conversationId", room.getConversationId() == null ? "" : room.getConversationId().toString());
        data.put("name", room.getName());
        data.put("description", room.getDescription() == null ? "" : room.getDescription());
        data.put("icon", room.getIcon() == null ? "" : room.getIcon());
        data.put("creatorId", room.getCreatorId() == null ? "" : room.getCreatorId().toString());
        data.put("creatorName", room.getCreatorName() == null ? "" : room.getCreatorName());
        data.put("memberCount", room.getMemberCount() == null ? 0 : room.getMemberCount());
        data.put("maxMembers", room.getMaxMembers() == null ? 500 : room.getMaxMembers());
        data.put("joined", joined);
        data.put("createdAt", room.getCreatedAt() == null ? "" : room.getCreatedAt().toString());
        data.put("updatedAt", room.getUpdatedAt() == null ? "" : room.getUpdatedAt().toString());
        return data;
    }
}