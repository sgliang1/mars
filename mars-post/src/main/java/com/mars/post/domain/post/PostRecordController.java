package com.mars.post.domain.post;

import com.mars.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/post")
public class PostRecordController {

    @Autowired
    private PostRecordService postRecordService;

    @GetMapping("/favorites")
    public Result<List<Map<String, Object>>> favorites(@RequestHeader("X-User-Id") String userIdStr) {
        Long userId = Long.parseLong(userIdStr);
        return Result.success(postRecordService.listFavorites(userId));
    }

    @PostMapping("/favorite/{postId}")
    public Result<Map<String, Object>> toggleFavorite(@PathVariable("postId") Long postId,
                                                      @RequestHeader("X-User-Id") String userIdStr) {
        Long userId = Long.parseLong(userIdStr);
        boolean favorited = postRecordService.toggleFavorite(userId, postId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("favorited", favorited);
        return Result.success(data);
    }

    @DeleteMapping("/favorite/{postId}")
    public Result<String> removeFavorite(@PathVariable("postId") Long postId,
                                         @RequestHeader("X-User-Id") String userIdStr) {
        Long userId = Long.parseLong(userIdStr);
        postRecordService.removeFavorite(userId, postId);
        return Result.successMessage("Favorite removed");
    }

    @DeleteMapping("/favorites")
    public Result<String> clearFavorites(@RequestHeader("X-User-Id") String userIdStr) {
        Long userId = Long.parseLong(userIdStr);
        postRecordService.clearFavorites(userId);
        return Result.successMessage("Favorites cleared");
    }

    @GetMapping("/history")
    public Result<List<Map<String, Object>>> history(@RequestHeader("X-User-Id") String userIdStr) {
        Long userId = Long.parseLong(userIdStr);
        return Result.success(postRecordService.listHistory(userId));
    }

    @PostMapping("/history/{postId}")
    public Result<String> addHistory(@PathVariable("postId") Long postId,
                                     @RequestHeader("X-User-Id") String userIdStr) {
        Long userId = Long.parseLong(userIdStr);
        postRecordService.recordHistory(userId, postId);
        return Result.successMessage("History recorded");
    }

    @DeleteMapping("/history/{postId}")
    public Result<String> removeHistory(@PathVariable("postId") Long postId,
                                        @RequestHeader("X-User-Id") String userIdStr) {
        Long userId = Long.parseLong(userIdStr);
        postRecordService.removeHistory(userId, postId);
        return Result.successMessage("History removed");
    }

    @DeleteMapping("/history")
    public Result<String> clearHistory(@RequestHeader("X-User-Id") String userIdStr) {
        Long userId = Long.parseLong(userIdStr);
        postRecordService.clearHistory(userId);
        return Result.successMessage("History cleared");
    }
}