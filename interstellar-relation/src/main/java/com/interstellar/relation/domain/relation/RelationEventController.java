package com.interstellar.relation.domain.relation;

import com.interstellar.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/relation/events")
@Tag(name = "关系动态", description = "关注/取关/拉黑等关系事件查询")
public class RelationEventController {

    @Autowired
    private RelationEventService relationEventService;

    @GetMapping
    @Operation(summary = "查询关系动态事件")
    public Result<List<UserRelationEvent>> getEvents(
            @RequestHeader("X-User-Id") String userIdStr,
            @Parameter(description = "事件类型: follow/unfollow/block/unblock") @RequestParam(required = false) String type,
            @Parameter(description = "起始时间（ISO格式）") @RequestParam(required = false) String since,
            @RequestParam(defaultValue = "50") int limit) {
        Long userId = Long.parseLong(userIdStr);
        LocalDateTime sinceTime = null;
        if (since != null && !since.isEmpty()) {
            sinceTime = LocalDateTime.parse(since, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
        return Result.success(relationEventService.getEvents(userId, type, sinceTime, limit));
    }
}