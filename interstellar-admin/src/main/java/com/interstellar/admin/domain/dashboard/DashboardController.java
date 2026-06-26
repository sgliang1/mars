package com.interstellar.admin.domain.dashboard;

import com.interstellar.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/dashboard")
@Tag(name = "数据看板", description = "平台数据统计")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    @GetMapping("/overview")
    @Operation(summary = "概览数据", description = "总用户、总帖子、今日新增")
    public Result<Map<String, Object>> overview() {
        return Result.success(dashboardService.getOverview());
    }

    @GetMapping("/trends")
    @Operation(summary = "趋势数据", description = "最近 30 天用户与内容增长")
    public Result<Map<String, Object>> trends() {
        return Result.success(dashboardService.getTrends());
    }

    @GetMapping("/active")
    @Operation(summary = "活跃数据", description = "DAU、发帖量、评论量、点赞量")
    public Result<Map<String, Object>> active() {
        return Result.success(dashboardService.getActive());
    }

    @GetMapping("/moderation")
    @Operation(summary = "审核指标", description = "待审核、已审核、通过率、举报趋势")
    public Result<Map<String, Object>> moderation() {
        return Result.success(dashboardService.getModeration());
    }

    @GetMapping("/alerts")
    @Operation(summary = "风险告警", description = "审核积压、多举报帖子、多举报用户")
    public Result<Map<String, Object>> alerts() {
        return Result.success(dashboardService.getAlerts());
    }
}