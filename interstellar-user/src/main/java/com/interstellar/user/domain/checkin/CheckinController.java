package com.interstellar.user.domain.checkin;

import com.interstellar.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/checkin")
@Tag(name = "每日签到", description = "每日签到与连续签到奖励")
public class CheckinController {

    @Autowired private CheckinService checkinService;

    @PostMapping
    @Operation(summary = "签到")
    public Result<Map<String, Object>> checkin(@RequestHeader("X-User-Id") String userIdStr) {
        try {
            Map<String, Object> result = checkinService.checkin(Long.parseLong(userIdStr));
            return Result.success(result);
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }

    @GetMapping("/status")
    @Operation(summary = "签到状态", description = "今日是否已签到、连续天数、累计签到次数")
    public Result<Map<String, Object>> status(@RequestHeader("X-User-Id") String userIdStr) {
        return Result.success(checkinService.getCheckinStatus(Long.parseLong(userIdStr)));
    }

    @GetMapping("/calendar")
    @Operation(summary = "签到日历", description = "按月查询签到记录")
    public Result<List<Map<String, Object>>> calendar(
            @RequestHeader("X-User-Id") String userIdStr,
            @RequestParam(value = "year", defaultValue = "2026") int year,
            @RequestParam(value = "month", defaultValue = "1") int month) {
        return Result.success(checkinService.getCheckinCalendar(Long.parseLong(userIdStr), year, month));
    }
}
