package com.interstellar.user.domain.reputation;

import com.interstellar.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/internal/reputation")
public class InternalReputationController {

    @Autowired private ReputationService reputationService;

    @PostMapping("/add")
    public Result<Void> add(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        int amount = Integer.parseInt(body.get("amount").toString());
        String sourceType = (String) body.get("sourceType");
        Long sourceId = body.get("sourceId") != null ? Long.valueOf(body.get("sourceId").toString()) : null;
        String description = (String) body.getOrDefault("description", "");
        reputationService.addReputation(userId, amount, sourceType, sourceId, description);
        return Result.success(null);
    }
}
