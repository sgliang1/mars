package com.mars.search.domain.search;

import com.mars.common.Result;
import com.mars.search.domain.filter.UserFilterService;
import com.mars.api.UserFeignClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/search")
@Tag(name = "搜索", description = "全文搜索帖子")
@ConditionalOnProperty(name = "search.elasticsearch.enabled", havingValue = "true")
public class SearchController {

    @Autowired
    private PostSearchRepository postSearchRepository;

    @Autowired
    private UserFilterService userFilterService;

    @Autowired
    private UserFeignClient userFeignClient;

    @GetMapping("/posts")
    @Operation(summary = "搜索帖子", description = "按关键词搜索帖子标题和内容")
    public Result<Page<PostDocument>> searchPosts(
            @Parameter(description = "搜索关键词") @RequestParam String q,
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "排序方式: relevance/time/hot") @RequestParam(defaultValue = "relevance") String sort,
            @RequestHeader(value = "X-User-Id", required = false) String userIdStr) {

        PageRequest pageRequest;
        if ("time".equals(sort)) {
            pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        } else if ("hot".equals(sort)) {
            pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "likeCount"));
        } else {
            pageRequest = PageRequest.of(page, size);
        }

        Page<PostDocument> results = postSearchRepository
                .findByTitleOrContentContaining(q, q, pageRequest);

        // 过滤 block/mute 用户的帖子
        if (userIdStr != null && results.hasContent()) {
            try {
                Long userId = Long.parseLong(userIdStr);
                Set<Long> filteredIds = userFilterService.getFilteredIds(userId);
                if (!filteredIds.isEmpty()) {
                    results = results.map(doc -> filteredIds.contains(doc.getUserId()) ? null : doc)
                            .map(doc -> doc);
                    Page<PostDocument> finalResults = results;
                    results = new org.springframework.data.domain.PageImpl<>(
                            finalResults.getContent().stream()
                                    .filter(doc -> doc != null && !filteredIds.contains(doc.getUserId()))
                                    .collect(Collectors.toList()),
                            finalResults.getPageable(),
                            finalResults.getTotalElements());
                }
            } catch (Exception ignored) {}
        }

        return Result.success(results);
    }

    @GetMapping("/suggest")
    @Operation(summary = "搜索建议", description = "根据关键词返回帖子标题建议")
    public Result<Iterable<PostDocument>> suggest(@RequestParam String q) {
        Page<PostDocument> results = postSearchRepository
                .findByTitleOrContentContaining(q, q, PageRequest.of(0, 10));
        return Result.success(results.getContent());
    }

    @GetMapping("/users")
    @Operation(summary = "搜索用户", description = "按用户名或昵称搜索用户")
    public Result<List<Map<String, Object>>> searchUsers(
            @RequestParam String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return userFeignClient.searchUsers(q, page, size);
    }
}
