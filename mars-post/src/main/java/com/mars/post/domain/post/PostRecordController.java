package com.mars.post.domain.post;

import com.mars.common.Result;
import com.mars.post.domain.post.PostRecordService;
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
        try {
            Long userId = Long.parseLong(userIdStr);
            return Result.success(postRecordService.listFavorites(userId));
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("Failed to load favorites");
        }
    }

    @PostMapping("/favorite/{postId}")
    public Result<Map<String, Object>> toggleFavorite(@PathVariable Long postId,
                                                      @RequestHeader("X-User-Id") String userIdStr) {
        try {
            Long userId = Long.parseLong(userIdStr);
            boolean favorited = postRecordService.toggleFavorite(userId, postId);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("favorited", favorited);
            return Result.success(data);
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("Failed to update favorite");
        }
    }

    @DeleteMapping("/favorite/{postId}")
    public Result<String> removeFavorite(@PathVariable Long postId,
                                         @RequestHeader("X-User-Id") String userIdStr) {
        try {
            Long userId = Long.parseLong(userIdStr);
            postRecordService.removeFavorite(userId, postId);
            return Result.successMessage("Favorite removed");
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("Failed to remove favorite");
        }
    }

    @DeleteMapping("/favorites")
    public Result<String> clearFavorites(@RequestHeader("X-User-Id") String userIdStr) {
        try {
            Long userId = Long.parseLong(userIdStr);
            postRecordService.clearFavorites(userId);
            return Result.successMessage("Favorites cleared");
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("Failed to clear favorites");
        }
    }

    @GetMapping("/history")
    public Result<List<Map<String, Object>>> history(@RequestHeader("X-User-Id") String userIdStr) {
        try {
            Long userId = Long.parseLong(userIdStr);
            return Result.success(postRecordService.listHistory(userId));
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("Failed to load history");
        }
    }

    @PostMapping("/history/{postId}")
    public Result<String> addHistory(@PathVariable Long postId,
                                     @RequestHeader("X-User-Id") String userIdStr) {
        try {
            Long userId = Long.parseLong(userIdStr);
            postRecordService.recordHistory(userId, postId);
            return Result.successMessage("History recorded");
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("Failed to record history");
        }
    }

    @DeleteMapping("/history/{postId}")
    public Result<String> removeHistory(@PathVariable Long postId,
                                        @RequestHeader("X-User-Id") String userIdStr) {
        try {
            Long userId = Long.parseLong(userIdStr);
            postRecordService.removeHistory(userId, postId);
            return Result.successMessage("History removed");
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("Failed to remove history");
        }
    }

    @DeleteMapping("/history")
    public Result<String> clearHistory(@RequestHeader("X-User-Id") String userIdStr) {
        try {
            Long userId = Long.parseLong(userIdStr);
            postRecordService.clearHistory(userId);
            return Result.successMessage("History cleared");
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("Failed to clear history");
        }
    }
}
