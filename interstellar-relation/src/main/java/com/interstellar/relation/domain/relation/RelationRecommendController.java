package com.interstellar.relation.domain.relation;

import com.interstellar.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/relation/recommend")
@Tag(name = "关系链推荐", description = "你可能认识的人")
public class RelationRecommendController {

    @Autowired
    private RelationRecommendService relationRecommendService;

    @GetMapping
    @Operation(summary = "获取推荐用户列表", description = "基于二度好友关系，按共同好友数排序")
    public Result<List<Map<String, Object>>> recommend(
            @RequestHeader("X-User-Id") String userIdStr,
            @RequestParam(defaultValue = "20") int limit) {
        Long userId = Long.parseLong(userIdStr);
        return Result.success(relationRecommendService.recommend(userId, limit));
    }
}