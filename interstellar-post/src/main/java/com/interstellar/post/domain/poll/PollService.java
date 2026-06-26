package com.interstellar.post.domain.poll;

import com.interstellar.common.util.SanitizeUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class PollService {

    @Autowired private PollMapper pollMapper;
    @Autowired private PollOptionMapper pollOptionMapper;
    @Autowired private PollVoteMapper pollVoteMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    /**
     * 创建投票（发帖时附带）
     */
    @Transactional
    public void createPoll(Long postId, String question, boolean isMultiple,
                           LocalDateTime expireAt, List<String> options) {
        Poll poll = new Poll();
        poll.setPostId(postId);
        poll.setQuestion(SanitizeUtil.stripHtml(question));
        poll.setIsMultiple(isMultiple ? 1 : 0);
        poll.setTotalVotes(0);
        poll.setExpireAt(expireAt);
        poll.setIsDeleted(0);
        poll.setCreateTime(LocalDateTime.now());
        pollMapper.insert(poll);

        for (int i = 0; i < options.size(); i++) {
            PollOption option = new PollOption();
            option.setPollId(poll.getId());
            option.setOptionText(SanitizeUtil.stripHtml(options.get(i)));
            option.setVoteCount(0);
            option.setSortOrder(i);
            pollOptionMapper.insert(option);
        }
    }

    /**
     * 投票
     */
    @Transactional
    public Map<String, Object> vote(Long pollId, Long userId, List<Long> optionIds) {
        Poll poll = pollMapper.selectById(pollId);
        if (poll == null) throw new RuntimeException("投票不存在");

        // 检查是否过期
        if (poll.getExpireAt() != null && LocalDateTime.now().isAfter(poll.getExpireAt())) {
            throw new RuntimeException("投票已截止");
        }

        // 单选：检查是否已投过
        if (poll.getIsMultiple() == 0) {
            Long existing = pollVoteMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PollVote>()
                            .eq(PollVote::getPollId, pollId)
                            .eq(PollVote::getUserId, userId));
            if (existing != null && existing > 0) {
                throw new RuntimeException("已投过票，不可重复投票");
            }
        } else {
            // 多选：检查是否投过同一选项
            for (Long optionId : optionIds) {
                Long existing = pollVoteMapper.selectCount(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PollVote>()
                                .eq(PollVote::getPollId, pollId)
                                .eq(PollVote::getUserId, userId)
                                .eq(PollVote::getOptionId, optionId));
                if (existing != null && existing > 0) {
                    throw new RuntimeException("选项已投过，不可重复投票");
                }
            }
        }

        // 写入投票记录
        for (Long optionId : optionIds) {
            PollVote vote = new PollVote();
            vote.setPollId(pollId);
            vote.setOptionId(optionId);
            vote.setUserId(userId);
            vote.setCreateTime(LocalDateTime.now());
            pollVoteMapper.insert(vote);

            // 递增选项票数
            jdbcTemplate.update(
                    "UPDATE poll_option SET vote_count = vote_count + 1 WHERE id = ?", optionId);
        }

        // 递增总票数
        jdbcTemplate.update(
                "UPDATE poll SET total_votes = total_votes + ? WHERE id = ?",
                optionIds.size(), pollId);

        return getPollResult(pollId, userId);
    }

    /**
     * 获取投票结果
     */
    public Map<String, Object> getPollResult(Long pollId, Long userId) {
        Poll poll = pollMapper.selectById(pollId);
        if (poll == null) throw new RuntimeException("投票不存在");

        List<PollOption> options = pollOptionMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PollOption>()
                        .eq(PollOption::getPollId, pollId)
                        .orderByAsc(PollOption::getSortOrder));

        // 当前用户已选的选项
        List<PollVote> userVotes = pollVoteMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PollVote>()
                        .eq(PollVote::getPollId, pollId)
                        .eq(PollVote::getUserId, userId));
        Set<Long> selectedIds = new HashSet<>();
        for (PollVote v : userVotes) selectedIds.add(v.getOptionId());

        Map<String, Object> result = new HashMap<>();
        result.put("pollId", pollId);
        result.put("question", poll.getQuestion());
        result.put("isMultiple", poll.getIsMultiple());
        result.put("totalVotes", poll.getTotalVotes());
        result.put("expireAt", poll.getExpireAt());
        result.put("isExpired", poll.getExpireAt() != null && LocalDateTime.now().isAfter(poll.getExpireAt()));
        result.put("hasVoted", !selectedIds.isEmpty());

        List<Map<String, Object>> optionResults = new ArrayList<>();
        for (PollOption opt : options) {
            Map<String, Object> optData = new HashMap<>();
            optData.put("optionId", opt.getId());
            optData.put("text", opt.getOptionText());
            optData.put("voteCount", opt.getVoteCount());
            double percentage = poll.getTotalVotes() > 0
                    ? Math.round(opt.getVoteCount() * 1000.0 / poll.getTotalVotes()) / 10.0
                    : 0;
            optData.put("percentage", percentage);
            optData.put("selected", selectedIds.contains(opt.getId()));
            optionResults.add(optData);
        }
        result.put("options", optionResults);

        return result;
    }

    /**
     * 根据帖子ID获取投票
     */
    public Poll getPollByPostId(Long postId) {
        return pollMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Poll>()
                        .eq(Poll::getPostId, postId)
                        .eq(Poll::getIsDeleted, 0));
    }
}