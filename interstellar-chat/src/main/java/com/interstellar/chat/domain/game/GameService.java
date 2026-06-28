package com.interstellar.chat.domain.game;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interstellar.chat.domain.conversation.ConversationMember;
import com.interstellar.chat.domain.conversation.ConversationMemberMapper;
import com.interstellar.chat.domain.message.ConversationMessage;
import com.interstellar.chat.domain.message.ConversationMessageMapper;
import com.interstellar.common.util.SanitizeUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class GameService {

    public static final int MSG_TYPE_GAME = 10;

    @Autowired
    private GameSessionMapper gameSessionMapper;

    @Autowired
    private ConversationMemberMapper conversationMemberMapper;

    @Autowired
    private ConversationMessageMapper conversationMessageMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== Create ====================

    @Transactional
    public Map<String, Object> createGame(Long userId, String username, Long conversationId,
                                          String civilianWord, String spyWord, int spyCount, int maxPlayers) {
        // Check no active game in this conversation
        Long activeCount = gameSessionMapper.selectCount(
                new LambdaQueryWrapper<GameSession>()
                        .eq(GameSession::getConversationId, conversationId)
                        .ne(GameSession::getStatus, "finished"));
        if (activeCount > 0) {
            throw new IllegalArgumentException("该房间已有进行中的游戏");
        }

        if (spyCount < 1 || spyCount > 3) spyCount = 1;
        if (maxPlayers < 4 || maxPlayers > 12) maxPlayers = 8;

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("civilianWord", SanitizeUtil.stripHtml(civilianWord));
        config.put("spyWord", SanitizeUtil.stripHtml(spyWord));
        config.put("spyCount", spyCount);
        config.put("maxPlayers", maxPlayers);

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("phase", "waiting");
        List<Map<String, Object>> players = new ArrayList<>();
        players.add(playerMap(userId, username, null, null, true));
        state.put("players", players);
        state.put("round", 0);
        state.put("currentTurn", -1);
        state.put("descriptions", new ArrayList<>());
        state.put("votes", new LinkedHashMap<>());

        GameSession session = new GameSession();
        session.setConversationId(conversationId);
        session.setGameType("spy");
        session.setCreatorId(userId);
        session.setStatus("waiting");
        session.setConfig(config);
        session.setState(state);
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        gameSessionMapper.insert(session);

        // Broadcast game created
        broadcastGameMessage(conversationId, userId, username, "game_created", session.getId(), state);

        return toGameStateMap(session, userId);
    }

    // ==================== Join ====================

    @Transactional
    public Map<String, Object> joinGame(Long userId, String username, Long gameId) {
        GameSession session = requireGame(gameId);
        if (!"waiting".equals(session.getStatus())) {
            throw new IllegalArgumentException("游戏已开始或已结束");
        }

        Map<String, Object> state = session.getState();
        List<Map<String, Object>> players = getPlayers(state);

        // Check if already joined
        for (Map<String, Object> p : players) {
            if (userId.equals(Long.parseLong(p.get("userId").toString()))) {
                return toGameStateMap(session, userId);
            }
        }

        int maxPlayers = getInt(session.getConfig(), "maxPlayers", 8);
        if (players.size() >= maxPlayers) {
            throw new IllegalArgumentException("游戏已满");
        }

        players.add(playerMap(userId, username, null, null, true));
        state.put("players", players);
        session.setState(state);
        session.setUpdatedAt(LocalDateTime.now());
        gameSessionMapper.updateById(session);

        broadcastGameMessage(session.getConversationId(), userId, username, "game_joined", gameId, state);

        return toGameStateMap(session, userId);
    }

    // ==================== Start ====================

    @Transactional
    public Map<String, Object> startGame(Long userId, Long gameId) {
        GameSession session = requireGame(gameId);
        if (!userId.equals(session.getCreatorId())) {
            throw new IllegalArgumentException("只有创建者可以开始游戏");
        }
        if (!"waiting".equals(session.getStatus())) {
            throw new IllegalArgumentException("游戏已开始或已结束");
        }

        Map<String, Object> state = session.getState();
        List<Map<String, Object>> players = getPlayers(state);
        if (players.size() < 4) {
            throw new IllegalArgumentException("至少需要4名玩家");
        }

        Map<String, Object> config = session.getConfig();
        String civilianWord = config.get("civilianWord").toString();
        String spyWord = config.get("spyWord").toString();
        int spyCount = getInt(config, "spyCount", 1);

        // Assign roles randomly
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) indices.add(i);
        Collections.shuffle(indices);

        for (int i = 0; i < players.size(); i++) {
            Map<String, Object> player = players.get(i);
            if (i < spyCount) {
                player.put("role", "spy");
                player.put("word", spyWord);
            } else {
                player.put("role", "civilian");
                player.put("word", civilianWord);
            }
        }

        state.put("phase", "describing");
        state.put("currentTurn", 0);
        state.put("round", 1);
        state.put("descriptions", new ArrayList<>());
        state.put("votes", new LinkedHashMap<>());
        session.setState(state);
        session.setStatus("playing");
        session.setUpdatedAt(LocalDateTime.now());
        gameSessionMapper.updateById(session);

        // Send private word to each player via game message
        for (Map<String, Object> player : players) {
            Long pid = Long.parseLong(player.get("userId").toString());
            String pname = player.get("name").toString();
            String word = player.get("word").toString();
            String role = player.get("role").toString();

            Map<String, Object> privateData = new LinkedHashMap<>();
            privateData.put("action", "your_word");
            privateData.put("gameId", gameId);
            privateData.put("word", word);
            privateData.put("role", role);
            privateData.put("playerCount", players.size());

            sendGameMessage(session.getConversationId(), pid, pname, privateData);
        }

        // Broadcast game started (without revealing roles)
        Map<String, Object> publicState = buildPublicState(state, userId);
        broadcastGameMessage(session.getConversationId(), userId, "", "game_started", gameId, publicState);

        return toGameStateMap(session, userId);
    }

    // ==================== Describe ====================

    @Transactional
    public Map<String, Object> submitDescription(Long userId, String username, Long gameId, String text) {
        GameSession session = requireGame(gameId);
        if (!"playing".equals(session.getStatus())) {
            throw new IllegalArgumentException("游戏未在进行中");
        }

        Map<String, Object> state = session.getState();
        String phase = state.get("phase").toString();
        if (!"describing".equals(phase)) {
            throw new IllegalArgumentException("当前不是描述阶段");
        }

        List<Map<String, Object>> players = getPlayers(state);
        int currentTurn = getInt(state, "currentTurn", 0);

        // Find current player
        List<Map<String, Object>> alivePlayers = players.stream()
                .filter(p -> Boolean.TRUE.equals(p.get("alive")))
                .toList();

        if (currentTurn >= alivePlayers.size()) {
            throw new IllegalArgumentException("描述已结束");
        }

        Map<String, Object> currentPlayer = alivePlayers.get(currentTurn);
        if (!userId.equals(Long.parseLong(currentPlayer.get("userId").toString()))) {
            throw new IllegalArgumentException("还没轮到你");
        }

        // Record description
        String safeText = SanitizeUtil.stripHtml(text);
        List<Map<String, Object>> descriptions = getDescriptions(state);
        Map<String, Object> desc = new LinkedHashMap<>();
        desc.put("userId", userId);
        desc.put("name", username);
        desc.put("text", safeText);
        desc.put("round", getInt(state, "round", 1));
        descriptions.add(desc);

        // Move to next turn
        int nextTurn = currentTurn + 1;
        state.put("currentTurn", nextTurn);

        // If all alive players have described, switch to voting
        if (nextTurn >= alivePlayers.size()) {
            state.put("phase", "voting");
            state.put("votes", new LinkedHashMap<>());
        }

        session.setState(state);
        session.setUpdatedAt(LocalDateTime.now());
        gameSessionMapper.updateById(session);

        Map<String, Object> publicState = buildPublicState(state, userId);
        broadcastGameMessage(session.getConversationId(), userId, username, "game_describe", gameId, publicState);

        return toGameStateMap(session, userId);
    }

    // ==================== Vote ====================

    @Transactional
    public Map<String, Object> submitVote(Long userId, String username, Long gameId, Long targetUserId) {
        GameSession session = requireGame(gameId);
        if (!"playing".equals(session.getStatus())) {
            throw new IllegalArgumentException("游戏未在进行中");
        }

        Map<String, Object> state = session.getState();
        if (!"voting".equals(state.get("phase").toString())) {
            throw new IllegalArgumentException("当前不是投票阶段");
        }

        // Check voter is alive
        List<Map<String, Object>> players = getPlayers(state);
        Map<String, Object> voter = findPlayer(players, userId);
        if (voter == null || !Boolean.TRUE.equals(voter.get("alive"))) {
            throw new IllegalArgumentException("你不能投票");
        }

        // Check target is alive
        Map<String, Object> target = findPlayer(players, targetUserId);
        if (target == null || !Boolean.TRUE.equals(target.get("alive"))) {
            throw new IllegalArgumentException("目标玩家不在游戏中");
        }

        // Record vote
        @SuppressWarnings("unchecked")
        Map<String, Object> votes = (Map<String, Object>) state.get("votes");
        votes.put(userId.toString(), targetUserId);
        state.put("votes", votes);
        session.setState(state);
        session.setUpdatedAt(LocalDateTime.now());
        gameSessionMapper.updateById(session);

        broadcastGameMessage(session.getConversationId(), userId, username, "game_vote", gameId, buildPublicState(state, userId));

        // Check if all alive players have voted
        List<Map<String, Object>> alivePlayers = players.stream()
                .filter(p -> Boolean.TRUE.equals(p.get("alive")))
                .toList();
        if (votes.size() >= alivePlayers.size()) {
            return processVoteResult(session);
        }

        return toGameStateMap(session, userId);
    }

    // ==================== Vote Result ====================

    private Map<String, Object> processVoteResult(GameSession session) {
        Map<String, Object> state = session.getState();
        List<Map<String, Object>> players = getPlayers(state);

        @SuppressWarnings("unchecked")
        Map<String, Object> votes = (Map<String, Object>) state.get("votes");

        // Count votes
        Map<Long, Integer> voteCount = new LinkedHashMap<>();
        for (Object targetObj : votes.values()) {
            Long targetId = Long.parseLong(targetObj.toString());
            voteCount.merge(targetId, 1, Integer::sum);
        }

        // Find the player with most votes
        Long eliminatedId = null;
        int maxVotes = 0;
        for (var entry : voteCount.entrySet()) {
            if (entry.getValue() > maxVotes) {
                maxVotes = entry.getValue();
                eliminatedId = entry.getKey();
            }
        }

        // Check for tie - if tie, no one is eliminated
        final int finalMaxVotes = maxVotes;
        boolean tie = voteCount.values().stream().filter(v -> v == finalMaxVotes).count() > 1;

        String resultMessage;
        String eliminatedRole = null;

        if (tie || eliminatedId == null) {
            resultMessage = "投票平局，本轮无人出局";
        } else {
            Map<String, Object> eliminated = findPlayer(players, eliminatedId);
            if (eliminated != null) {
                eliminated.put("alive", false);
                eliminatedRole = eliminated.get("role").toString();
                String eliminatedName = eliminated.get("name").toString();
                resultMessage = eliminatedName + " 被投票淘汰，身份是：" +
                        ("spy".equals(eliminatedRole) ? "卧底" : "平民");
            } else {
                resultMessage = "投票异常";
            }
        }

        // Check win condition
        long aliveSpyCount = players.stream()
                .filter(p -> Boolean.TRUE.equals(p.get("alive")) && "spy".equals(p.get("role")))
                .count();
        long aliveCivilianCount = players.stream()
                .filter(p -> Boolean.TRUE.equals(p.get("alive")) && "civilian".equals(p.get("role")))
                .count();

        String winner = null;
        if (aliveSpyCount == 0) {
            winner = "civilian";
            session.setStatus("finished");
        } else if (aliveSpyCount >= aliveCivilianCount) {
            winner = "spy";
            session.setStatus("finished");
        }

        if (winner != null) {
            state.put("phase", "finished");
            state.put("winner", winner);
        } else {
            // Next round
            state.put("phase", "describing");
            state.put("currentTurn", 0);
            state.put("round", getInt(state, "round", 1) + 1);
            state.put("descriptions", new ArrayList<>());
            state.put("votes", new LinkedHashMap<>());
        }

        // Build result data
        Map<String, Object> resultData = new LinkedHashMap<>();
        resultData.put("action", "vote_result");
        resultData.put("gameId", session.getId());
        resultData.put("resultMessage", resultMessage);
        resultData.put("eliminatedId", eliminatedId);
        resultData.put("eliminatedRole", eliminatedRole);
        resultData.put("winner", winner);
        resultData.put("state", buildPublicState(state, 0L));

        // Broadcast result
        broadcastGameMessage(session.getConversationId(), session.getCreatorId(), "", "vote_result", session.getId(), resultData);

        session.setState(state);
        session.setUpdatedAt(LocalDateTime.now());
        gameSessionMapper.updateById(session);

        return toGameStateMap(session, 0L);
    }

    // ==================== Get State ====================

    public Map<String, Object> getGameState(Long gameId, Long userId) {
        GameSession session = requireGame(gameId);
        return toGameStateMap(session, userId);
    }

    public List<Map<String, Object>> listGames(Long conversationId) {
        List<GameSession> sessions = gameSessionMapper.selectList(
                new LambdaQueryWrapper<GameSession>()
                        .eq(GameSession::getConversationId, conversationId)
                        .ne(GameSession::getStatus, "finished")
                        .orderByDesc(GameSession::getCreatedAt));
        return sessions.stream().map(s -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", s.getId().toString());
            data.put("gameType", s.getGameType());
            data.put("status", s.getStatus());
            data.put("creatorId", s.getCreatorId().toString());
            data.put("createdAt", s.getCreatedAt() == null ? "" : s.getCreatedAt().toString());
            return data;
        }).toList();
    }

    // ==================== Helpers ====================

    private GameSession requireGame(Long gameId) {
        GameSession session = gameSessionMapper.selectById(gameId);
        if (session == null) throw new IllegalArgumentException("游戏不存在");
        return session;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getPlayers(Map<String, Object> state) {
        return (List<Map<String, Object>>) state.get("players");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getDescriptions(Map<String, Object> state) {
        return (List<Map<String, Object>>) state.get("descriptions");
    }

    private Map<String, Object> findPlayer(List<Map<String, Object>> players, Long userId) {
        for (Map<String, Object> p : players) {
            if (userId.equals(Long.parseLong(p.get("userId").toString()))) return p;
        }
        return null;
    }

    private Map<String, Object> playerMap(Long userId, String name, String role, String word, boolean alive) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("userId", userId);
        p.put("name", name);
        p.put("role", role);
        p.put("word", word);
        p.put("alive", alive);
        return p;
    }

    private int getInt(Map<String, Object> map, String key, int defaultVal) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.intValue();
        return defaultVal;
    }

    /** Build public state (hide other players' words and roles) */
    private Map<String, Object> buildPublicState(Map<String, Object> state, Long userId) {
        Map<String, Object> pub = new LinkedHashMap<>(state);
        List<Map<String, Object>> players = getPlayers(state);
        List<Map<String, Object>> safePlayers = new ArrayList<>();
        for (Map<String, Object> p : players) {
            Map<String, Object> sp = new LinkedHashMap<>();
            sp.put("userId", p.get("userId"));
            sp.put("name", p.get("name"));
            sp.put("alive", p.get("alive"));
            // Only reveal role if player is dead or game is finished
            boolean isFinished = "finished".equals(state.get("phase"));
            boolean isAlive = Boolean.TRUE.equals(p.get("alive"));
            if (isFinished || !isAlive) {
                sp.put("role", p.get("role"));
            }
            safePlayers.add(sp);
        }
        pub.put("players", safePlayers);
        return pub;
    }

    private Map<String, Object> toGameStateMap(GameSession session, Long userId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", session.getId().toString());
        data.put("conversationId", session.getConversationId().toString());
        data.put("gameType", session.getGameType());
        data.put("creatorId", session.getCreatorId().toString());
        data.put("status", session.getStatus());
        data.put("config", session.getConfig());
        data.put("state", buildPublicState(session.getState(), userId));
        data.put("createdAt", session.getCreatedAt() == null ? "" : session.getCreatedAt().toString());
        return data;
    }

    private void broadcastGameMessage(Long conversationId, Long userId, String username, String action, Long gameId, Object stateData) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", action);
        payload.put("gameId", gameId);
        payload.put("data", stateData);

        ConversationMessage msg = new ConversationMessage();
        msg.setConversationId(conversationId);
        msg.setSenderId(userId);
        msg.setSenderName(username);
        msg.setContent(toJson(payload));
        msg.setMessageType(MSG_TYPE_GAME);
        msg.setDeliveryStatus("sent");
        msg.setCreatedAt(LocalDateTime.now());
        conversationMessageMapper.insert(msg);
    }

    private void sendGameMessage(Long conversationId, Long userId, String username, Map<String, Object> payload) {
        ConversationMessage msg = new ConversationMessage();
        msg.setConversationId(conversationId);
        msg.setSenderId(userId);
        msg.setSenderName(username);
        msg.setContent(toJson(payload));
        msg.setMessageType(MSG_TYPE_GAME);
        msg.setDeliveryStatus("sent");
        msg.setCreatedAt(LocalDateTime.now());
        conversationMessageMapper.insert(msg);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
