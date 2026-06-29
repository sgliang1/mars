package com.interstellar.chat.domain.room;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interstellar.chat.domain.conversation.Conversation;
import com.interstellar.chat.domain.conversation.ConversationMapper;
import com.interstellar.chat.domain.conversation.ConversationMember;
import com.interstellar.chat.domain.conversation.ConversationMemberMapper;
import com.interstellar.chat.domain.message.ConversationMessage;
import com.interstellar.chat.domain.message.ConversationMessageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ClubChannelService {

    @Autowired
    private ClubChannelMapper clubChannelMapper;
    @Autowired
    private ChatRoomMapper chatRoomMapper;
    @Autowired
    private ConversationMapper conversationMapper;
    @Autowired
    private ConversationMemberMapper conversationMemberMapper;
    @Autowired
    private ConversationMessageMapper conversationMessageMapper;

    /**
     * 发起俱乐部通讯邀请
     */
    @Transactional
    public Map<String, Object> createChannel(Long userId, Long clubAId, Long clubBId) {
        if (clubAId.equals(clubBId)) throw new IllegalArgumentException("不能与自己建立通讯");

        ChatRoom clubA = requireClub(clubAId);
        ChatRoom clubB = requireClub(clubBId);

        // 检查是否已存在
        ClubChannel existing = clubChannelMapper.selectOne(
                new LambdaQueryWrapper<ClubChannel>()
                        .eq(ClubChannel::getClubAId, Math.min(clubAId, clubBId))
                        .eq(ClubChannel::getClubBId, Math.max(clubAId, clubBId)));
        if (existing != null) throw new IllegalArgumentException("通讯已存在");

        // 创建对话
        LocalDateTime now = LocalDateTime.now();
        Conversation conversation = new Conversation();
        conversation.setType("group");
        conversation.setBizKey("channel-pending");
        conversation.setTitle(clubA.getName() + " ⟷ " + clubB.getName());
        conversation.setDescription("俱乐部通讯频段");
        conversation.setStatus(1);
        conversation.setLastMessageAt(now);
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);
        conversationMapper.insert(conversation);
        conversation.setBizKey("channel-" + conversation.getId());
        conversationMapper.updateById(conversation);

        // 创建通讯记录
        ClubChannel channel = new ClubChannel();
        channel.setClubAId(Math.min(clubAId, clubBId));
        channel.setClubBId(Math.max(clubAId, clubBId));
        channel.setConversationId(conversation.getId());
        channel.setStatus("active");
        channel.setCreatedBy(userId);
        channel.setCreatedAt(now);
        clubChannelMapper.insert(channel);

        // 将两个俱乐部的所有成员加入对话
        addClubMembersToConversation(clubA.getConversationId(), conversation.getId());
        addClubMembersToConversation(clubB.getConversationId(), conversation.getId());

        // 欢迎消息
        ConversationMessage msg = new ConversationMessage();
        msg.setConversationId(conversation.getId());
        msg.setSenderId(0L);
        msg.setSenderName("系统");
        msg.setContent("俱乐部「" + clubA.getName() + "」和「" + clubB.getName() + "」已建立通讯频段");
        msg.setMessageType(0);
        msg.setDeliveryStatus("sent");
        msg.setCreatedAt(now);
        conversationMessageMapper.insert(msg);

        return toChannelMap(channel, clubA.getName(), clubB.getName());
    }

    /**
     * 获取俱乐部的所有通讯
     */
    public List<Map<String, Object>> listChannels(Long clubId) {
        List<ClubChannel> channels = clubChannelMapper.selectList(
                new LambdaQueryWrapper<ClubChannel>()
                        .eq(ClubChannel::getStatus, "active")
                        .and(w -> w.eq(ClubChannel::getClubAId, clubId)
                                   .or().eq(ClubChannel::getClubBId, clubId)));

        List<Map<String, Object>> result = new ArrayList<>();
        for (ClubChannel ch : channels) {
            ChatRoom clubA = chatRoomMapper.selectById(ch.getClubAId());
            ChatRoom clubB = chatRoomMapper.selectById(ch.getClubBId());
            String nameA = clubA != null ? clubA.getName() : "未知";
            String nameB = clubB != null ? clubB.getName() : "未知";
            result.add(toChannelMap(ch, nameA, nameB));
        }
        return result;
    }

    /**
     * 关闭通讯
     */
    @Transactional
    public void closeChannel(Long userId, Long channelId) {
        ClubChannel channel = clubChannelMapper.selectById(channelId);
        if (channel == null) throw new IllegalArgumentException("通讯不存在");
        channel.setStatus("closed");
        clubChannelMapper.updateById(channel);
    }

    private void addClubMembersToConversation(Long sourceConversationId, Long targetConversationId) {
        if (sourceConversationId == null) return;
        List<ConversationMember> members = conversationMemberMapper.selectList(
                new LambdaQueryWrapper<ConversationMember>()
                        .eq(ConversationMember::getConversationId, sourceConversationId));
        LocalDateTime now = LocalDateTime.now();
        for (ConversationMember m : members) {
            // 检查是否已在目标对话中
            Long exists = conversationMemberMapper.selectCount(
                    new LambdaQueryWrapper<ConversationMember>()
                            .eq(ConversationMember::getConversationId, targetConversationId)
                            .eq(ConversationMember::getUserId, m.getUserId()));
            if (exists != null && exists > 0) continue;

            ConversationMember newMember = new ConversationMember();
            newMember.setConversationId(targetConversationId);
            newMember.setUserId(m.getUserId());
            newMember.setRole("member");
            newMember.setUnreadCount(0);
            newMember.setMuted(false);
            newMember.setPinned(false);
            newMember.setArchived(false);
            newMember.setJoinedAt(now);
            conversationMemberMapper.insert(newMember);
        }
    }

    private ChatRoom requireClub(Long clubId) {
        ChatRoom room = chatRoomMapper.selectById(clubId);
        if (room == null || room.getStatus() == null || room.getStatus() != 1) {
            throw new IllegalArgumentException("俱乐部不存在");
        }
        return room;
    }

    private Map<String, Object> toChannelMap(ClubChannel channel, String clubAName, String clubBName) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", channel.getId().toString());
        data.put("clubAId", channel.getClubAId().toString());
        data.put("clubBId", channel.getClubBId().toString());
        data.put("clubAName", clubAName);
        data.put("clubBName", clubBName);
        data.put("conversationId", channel.getConversationId().toString());
        data.put("status", channel.getStatus());
        data.put("createdBy", channel.getCreatedBy().toString());
        data.put("createdAt", channel.getCreatedAt() == null ? "" : channel.getCreatedAt().toString());
        return data;
    }
}
