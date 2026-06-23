package com.mars.post.domain.report;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mars.common.Result;
import com.mars.post.domain.comment.Comment;
import com.mars.post.domain.comment.CommentMapper;
import com.mars.post.domain.post.Post;
import com.mars.post.domain.post.PostMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/reports")
@Tag(name = "举报", description = "用户举报接口")
public class ReportController {

    @Autowired
    private ReportMapper reportMapper;

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private CommentMapper commentMapper;

    @PostMapping
    @Operation(summary = "提交举报")
    public Result<Void> create(
            @Valid @RequestBody ReportDTO dto,
            @RequestHeader("X-User-Id") String userIdStr) {

        Long reporterId = Long.parseLong(userIdStr);

        // 1. 校验 targetType 合法性
        if (!List.of("post", "comment", "user").contains(dto.getTargetType())) {
            return Result.fail("无效的举报类型");
        }

        // 2. 防止举报自己（帖子/评论需查 owner，用户类型直接比对 ID）
        if ("user".equals(dto.getTargetType()) && reporterId.equals(dto.getTargetId())) {
            return Result.fail("不能举报自己");
        }
        if ("post".equals(dto.getTargetType())) {
            Post post = postMapper.selectById(dto.getTargetId());
            if (post == null) return Result.fail("帖子不存在");
            if (reporterId.equals(post.getUserId())) return Result.fail("不能举报自己的帖子");
        }
        if ("comment".equals(dto.getTargetType())) {
            Comment comment = commentMapper.selectById(dto.getTargetId());
            if (comment == null) return Result.fail("评论不存在");
            if (reporterId.equals(comment.getUserId())) return Result.fail("不能举报自己的评论");
        }

        // 3. 防止重复举报（同一用户对同一目标只允许一条待处理举报）
        Long exists = reportMapper.selectCount(
                new LambdaQueryWrapper<Report>()
                        .eq(Report::getReporterId, reporterId)
                        .eq(Report::getTargetType, dto.getTargetType())
                        .eq(Report::getTargetId, dto.getTargetId())
                        .eq(Report::getStatus, 0));
        if (exists > 0) return Result.fail("您已举报过该内容，请等待处理");

        // 4. 写入
        Report report = new Report();
        report.setReporterId(reporterId);
        report.setTargetType(dto.getTargetType());
        report.setTargetId(dto.getTargetId());
        report.setReason(dto.getReason());
        report.setDescription(dto.getDescription());
        report.setStatus(0);
        report.setCreatedAt(LocalDateTime.now());
        reportMapper.insert(report);

        return Result.successMessage("举报已提交");
    }
}
