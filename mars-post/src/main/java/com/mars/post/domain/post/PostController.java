package com.mars.post.domain.post;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mars.common.Result;
import com.mars.post.domain.post.PostDTO;
import com.mars.post.domain.post.Post;
import com.mars.post.domain.post.PostContent;
import com.mars.post.domain.post.PostImage;
import com.mars.post.domain.post.PostLike;
import com.mars.post.domain.post.PostContentMapper;
import com.mars.post.domain.post.PostImageMapper;
import com.mars.post.domain.post.PostLikeMapper;
import com.mars.post.domain.post.PostMapper;
import com.mars.post.domain.post.PostService;
import com.mars.post.domain.topic.PostTopic;
import com.mars.post.domain.topic.PostTopicMapper;
import com.mars.post.infrastructure.file.S3Service;
import com.mars.common.cache.CacheKeys;
import com.mars.common.cache.CacheService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/posts")
@Tag(name = "帖子", description = "帖子发布、查询、点赞")
public class PostController {

    @Autowired private PostService postService;
    @Autowired private PostMapper postMapper;
    @Autowired private PostContentMapper postContentMapper;
    @Autowired private PostLikeMapper postLikeMapper;
    @Autowired private PostImageMapper postImageMapper;
    @Autowired private S3Service s3Service;
    @Autowired private CacheService cacheService;
    @Autowired private MentionService mentionService;
    @Autowired private com.mars.post.domain.poll.PollService pollService;
    @Autowired private com.mars.post.domain.filter.UserFilterService userFilterService;
    @Autowired private PostTopicMapper postTopicMapper;


    @Value("${file.local-path}")
    private String localPath;

    /**
     * 发布帖子
     * 修改说明：完全移�?JWT 解析，直接使用网关传过来�?ID �?用户�?
     */
    @PostMapping("")
    @Operation(summary = "发布帖子")
    public Result<String> add(@Valid @RequestBody PostDTO postDTO,
                              @RequestHeader("X-User-Id") String userIdStr,
                              @RequestHeader(value = "X-User-Name", required = false) String encodedUsername) {
        try {
            // 1. 获取 ID (网关已鉴权，这里直接�?Long)
            Long userId = Long.parseLong(userIdStr);

            // 信用分检查：低于 60 分禁止发帖
            Object creditObj = cacheService.get(CacheKeys.key(CacheKeys.USER_CREDIT, userId));
            if (creditObj != null) {
                int credit = Integer.parseInt(creditObj.toString());
                if (credit < 60) {
                    return Result.fail("信用分不足，暂时无法发帖");
                }
            }

            // 2. 获取用户名(需解码，防止乱码)
            String username = "匿名用户";
            if (encodedUsername != null) {
                username = URLDecoder.decode(encodedUsername, StandardCharsets.UTF_8.name());
            }

            // 3. 组装实体
            Post post = new Post();
            post.setUserId(userId);
            post.setUsername(username);
            // 前端未传 title 时，从正文第一行自动生成
            String title = postDTO.getTitle();
            if (title == null || title.isBlank()) {
                String content = postDTO.getContent();
                if (content != null && !content.isBlank()) {
                    title = content.contains("\n")
                        ? content.substring(0, content.indexOf("\n")).trim()
                        : content.length() > 30 ? content.substring(0, 30) : content;
                } else {
                    title = "未命名帖子";
                }
            }
            post.setTitle(title);
            post.setLikeCount(0);
            post.setCommentCount(0);
            post.setShareCount(0);
            post.setVisibility(postDTO.getVisibility() != null ? postDTO.getVisibility() : 0);

            // Phase 4.4: 位置信息
            post.setLocationName(postDTO.getLocationName());
            post.setLatitude(postDTO.getLatitude());
            post.setLongitude(postDTO.getLongitude());

            // Phase 3.3: 定时发布
            if (postDTO.getScheduledAt() != null) {
                if (postDTO.getScheduledAt().isBefore(LocalDateTime.now())) {
                    return Result.fail("定时发布时间必须是未来时间");
                }
                post.setScheduledAt(postDTO.getScheduledAt());
                post.setAuditStatus(0);      // 待审核
                post.setDisplayStatus(0);    // 待发布
            } else {
                post.setAuditStatus(2);      // 人审通过（直接发布）
                post.setDisplayStatus(1);    // 已发布
            }

            // 4. 调用业务 Service 保存 (主表+内容)
            postService.publish(post, postDTO.getContent());

            // 5. 保存图片
            if (postDTO.getImages() != null && !postDTO.getImages().isEmpty()) {
                String[] urls = postDTO.getImages().split(",");
                for (int i = 0; i < urls.length; i++) {
                    PostImage img = new PostImage();
                    img.setPostId(post.getId()); // ID 由 MyBatis Plus 回填
                    img.setUrl(urls[i].trim());
                    img.setSort(i);
                    postImageMapper.insert(img);
                }
            }

            // 6. 处理 @提及
            if (postDTO.getMentionUserIds() != null && !postDTO.getMentionUserIds().isEmpty()) {
                mentionService.parseAndSave(post.getId(), null, postDTO.getContent(),
                        userId, username, postDTO.getMentionUserIds());
            }

            // 7. 创建投票
            if (postDTO.getPollQuestion() != null && postDTO.getPollOptions() != null
                    && !postDTO.getPollQuestion().isBlank() && !postDTO.getPollOptions().isEmpty()) {
                pollService.createPoll(post.getId(), postDTO.getPollQuestion(),
                        Boolean.TRUE.equals(postDTO.getPollMultiple()),
                        postDTO.getPollExpireAt(), postDTO.getPollOptions());
            }

            // 8. 关联话题
            if (postDTO.getTopicIds() != null && !postDTO.getTopicIds().isEmpty()) {
                for (Long topicId : postDTO.getTopicIds()) {
                    PostTopic pt = new PostTopic();
                    pt.setPostId(post.getId());
                    pt.setTopicId(topicId);
                    postTopicMapper.insert(pt);
                }
            }

            return Result.successMessage(postDTO.getScheduledAt() != null ? "定时发布设置成功" : "发布成功");
        } catch (Exception e) {
            log.error("发布帖子失败: userId={}", userIdStr, e);
            return Result.fail("发布失败，请稍后重试");
        }
    }

    /**
     * 首页列表
     */
    @GetMapping("")
    @Operation(summary = "帖子列表", description = "置顶帖优先，然后按时间倒序分页，支持可见范围过滤")
    public Result<List<Post>> list(@Parameter(description = "当前用户ID") @RequestHeader(value = "X-User-Id", required = false) String userIdStr,
                                   @RequestParam(value = "page", defaultValue = "1") int page,
                                   @RequestParam(value = "size", defaultValue = "20") int size) {
        Page<Post> pageParam = new Page<>(page, size);
        QueryWrapper<Post> wrapper = new QueryWrapper<Post>()
                .isNull("deleted_at")
                .orderByDesc("is_pinned", "pinned_at", "create_time");

        // 可见范围过滤：未登录用户只能看公开帖子
        if (userIdStr == null) {
            wrapper.eq("visibility", 0);
        } else {
            // 已登录：不过滤自己的帖子，其他帖子只显示公开的（简化处理，复杂的粉丝/好友关系后续优化）
            Long userId = Long.parseLong(userIdStr);
            wrapper.and(w -> w.eq("visibility", 0).or().eq("user_id", userId));
        }

        Page<Post> result = postMapper.selectPage(pageParam, wrapper);
        List<Post> posts = result.getRecords();

        // 过滤 block/mute 用户的帖子
        if (userIdStr != null) {
            try {
                Long userId = Long.parseLong(userIdStr);
                Set<Long> filteredIds = userFilterService.getFilteredIds(userId);
                if (!filteredIds.isEmpty()) {
                    posts = posts.stream().filter(p -> !filteredIds.contains(p.getUserId())).collect(Collectors.toList());
                }
                // 过滤 postVisible=0 的作者帖子
                posts = posts.stream().filter(p -> {
                    if (p.getUserId().equals(userId)) return true;
                    String postVisibleKey = CacheKeys.relationKey(CacheKeys.RELATION_POST_VISIBLE, p.getUserId(), userId);
                    Object postVisible = cacheService.get(postVisibleKey);
                    return postVisible == null || !"0".equals(postVisible.toString());
                }).collect(Collectors.toList());
            } catch (Exception e) {
                log.warn("过滤用户帖子异常: userId={}", userIdStr, e);
            }
        }

        return Result.success(attachPostExtras(posts, userIdStr));
    }

    /**
     * 当前用户的帖子列表
     */
    @GetMapping("/mine")
    public Result<List<Post>> mine(@RequestHeader("X-User-Id") String userIdStr,
                                   @RequestParam(value = "page", defaultValue = "1") int page,
                                   @RequestParam(value = "size", defaultValue = "20") int size) {
        Long userId = Long.parseLong(userIdStr);
        Page<Post> pageParam = new Page<>(page, size);
        Page<Post> result = postMapper.selectPage(pageParam, new QueryWrapper<Post>()
                .eq("user_id", userId)
                .isNull("deleted_at")
                .orderByDesc("create_time"));
        return Result.success(attachPostExtras(result.getRecords(), userIdStr));
    }

    private List<Post> attachPostExtras(List<Post> postList, String userIdStr) {
        if (postList.isEmpty()) return postList;

        List<Long> postIds = postList.stream().map(Post::getId).collect(Collectors.toList());
        List<PostImage> allImages = postImageMapper.selectList(new LambdaQueryWrapper<PostImage>()
                .in(PostImage::getPostId, postIds)
                .orderByAsc(PostImage::getSort));

        Map<Long, List<String>> imageMap = new HashMap<>();
        for (PostImage img : allImages) {
            imageMap.computeIfAbsent(img.getPostId(), k -> new ArrayList<>()).add(img.getUrl());
        }

        for (Post post : postList) {
            post.setImageList(imageMap.getOrDefault(post.getId(), new ArrayList<>()));
        }

        if (userIdStr != null) {
            try {
                Long userId = Long.parseLong(userIdStr);
                List<PostLike> likes = postLikeMapper.selectList(new LambdaQueryWrapper<PostLike>()
                        .eq(PostLike::getUserId, userId)
                        .in(PostLike::getPostId, postIds));
                Set<Long> likedPostIds = likes.stream().map(PostLike::getPostId).collect(Collectors.toSet());

                postList.forEach(p -> p.setLiked(likedPostIds.contains(p.getId())));
            } catch (Exception e) {
                log.warn("查询帖子点赞状态异常", e);
            }
        }

        return postList;
    }

    /**
     * 帖子详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "帖子详情")
    public Result<Map<String, Object>> getDetail(@Parameter(description = "帖子ID") @PathVariable("id") Long id,
                                                 @RequestHeader(value = "X-User-Id", required = false) String userIdStr) {
        // 尝试从缓存获取帖子公共数据
        String cacheKey = CacheKeys.key(CacheKeys.POST_DETAIL, id);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = cacheService.get(cacheKey);

        if (data == null) {
            Post post = postMapper.selectById(id);
            if (post == null || post.getDeletedAt() != null) return Result.fail("内容不存在");
            PostContent content = postContentMapper.selectById(id);

            List<PostImage> images = postImageMapper.selectList(new LambdaQueryWrapper<PostImage>()
                    .eq(PostImage::getPostId, id)
                    .orderByAsc(PostImage::getSort));
            List<String> imageUrls = images.stream().map(PostImage::getUrl).collect(Collectors.toList());

            data = new HashMap<>();

            String body = (content != null && content.getContent() != null) ? content.getContent() : "";
            if (body.isEmpty()) {
                body = post.getSummary() != null ? post.getSummary() : "";
            }
            data.put("content", body);

            String title = post.getTitle();
            if (title == null || title.isBlank()) {
                if (!body.isEmpty()) {
                    title = body.contains("\n")
                        ? body.substring(0, body.indexOf("\n")).trim()
                        : body.length() > 30 ? body.substring(0, 30) : body;
                } else {
                    title = "未命名帖子";
                }
            }

            data.put("id", post.getId());
            data.put("title", title);
            data.put("username", post.getUsername());
            data.put("userId", post.getUserId());
            data.put("imageList", imageUrls);
            data.put("likeCount", post.getLikeCount());
            data.put("commentCount", post.getCommentCount());
            data.put("shareCount", post.getShareCount());
            data.put("viewCount", post.getViewCount() != null ? post.getViewCount() : 0);
            data.put("isPinned", post.getIsPinned());
            data.put("isFeatured", post.getIsFeatured());
            data.put("createTime", post.getCreateTime());
            data.put("auditStatus", post.getAuditStatus());
            data.put("displayStatus", post.getDisplayStatus());
            data.put("reviewReason", post.getReviewReason());

            // 缓存公共数据（不含用户特定的 isLiked）
            cacheService.set(cacheKey, data, CacheKeys.POST_DETAIL_TTL);
        }

        // 检查帖子作者是否被当前用户 block/mute
        if (userIdStr != null) {
            try {
                Long userId = Long.parseLong(userIdStr);
                Long postAuthorId = (Long) data.get("userId");
                if (postAuthorId != null && userFilterService.isFiltered(userId, postAuthorId)) {
                    return Result.fail("内容不存在");
                }
                // 检查帖子作者是否对当前用户设置了 postVisible=0
                if (postAuthorId != null && !postAuthorId.equals(userId)) {
                    String postVisibleKey = CacheKeys.relationKey(CacheKeys.RELATION_POST_VISIBLE, postAuthorId, userId);
                    Object postVisible = cacheService.get(postVisibleKey);
                    if (postVisible != null && "0".equals(postVisible.toString())) {
                        return Result.fail("内容不存在");
                    }
                }
            } catch (Exception e) {
                log.warn("检查帖子作者过滤状态异常: postId={}", id, e);
            }
        }

        // isLiked 每次请求单独查询（用户特定）
        boolean isLiked = false;
        if (userIdStr != null) {
            try {
                Long userId = Long.parseLong(userIdStr);
                Long count = postLikeMapper.selectCount(new LambdaQueryWrapper<PostLike>()
                        .eq(PostLike::getPostId, id).eq(PostLike::getUserId, userId));
                isLiked = count > 0;
            } catch (Exception e) {
                log.warn("查询帖子点赞状态异常: postId={}", id, e);
            }
        }
        data.put("isLiked", isLiked);

        // 浏览量：Redis 累积计数 + 响应返回实时值（定时任务回写 DB，不破坏缓存）
        try {
            int baseViewCount = ((Number) data.getOrDefault("viewCount", 0)).intValue();
            String viewCountKey = CacheKeys.key(CacheKeys.COUNT_VIEWS, id);
            Long delta = cacheService.increment(viewCountKey);
            data.put("viewCount", baseViewCount + (delta != null ? delta.intValue() : 1));
        } catch (Exception e) {
            log.warn("递增浏览量异常: postId={}", id, e);
        }

        return Result.success(data);
    }

    /**
     * 更新帖子
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新帖子")
    public Result<String> update(@Parameter(description = "帖子ID") @PathVariable("id") Long id,
                                 @Valid @RequestBody PostDTO postDTO,
                                 @RequestHeader("X-User-Id") String userIdStr) {
        try {
            Long userId = Long.parseLong(userIdStr);
            boolean updated = postService.updatePost(id, userId, postDTO);
            if (!updated) {
                return Result.fail("帖子不存在或无权限修改");
            }
            return Result.successMessage("更新成功");
        } catch (Exception e) {
            log.error("更新帖子失败: postId={}, userId={}", id, userIdStr, e);
            return Result.fail("更新失败，请稍后重试");
        }
    }

    /**
     * 删除帖子
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除帖子")
    public Result<String> delete(@Parameter(description = "帖子ID") @PathVariable("id") Long id,
                                 @RequestHeader("X-User-Id") String userIdStr) {
        try {
            Long userId = Long.parseLong(userIdStr);
            boolean deleted = postService.deletePost(id, userId);
            if (!deleted) {
                return Result.fail("帖子不存在或无权限删除");
            }
            return Result.successMessage("删除成功");
        } catch (Exception e) {
            log.error("删除帖子失败: postId={}, userId={}", id, userIdStr, e);
            return Result.fail("删除失败，请稍后重试");
        }
    }

    /**
     * 点赞接口
     */
    @PostMapping("/{postId}/like")
    @Operation(summary = "点赞/取消点赞", description = "切换点赞状态")
    public Result like(@Parameter(description = "帖子ID") @PathVariable("postId") Long postId,
                       @RequestHeader(value = "X-User-Id", required = false) String userIdStr,
                       @RequestHeader(value = "X-User-Name", required = false) String encodedUsername) {
        if (userIdStr == null) return Result.fail("请先登录");
        Long userId = Long.parseLong(userIdStr);

        String username = "匿名用户";
        if (encodedUsername != null) {
            try {
                username = URLDecoder.decode(encodedUsername, StandardCharsets.UTF_8.name());
            } catch (Exception e) {
                log.warn("URL解码用户名失败", e);
            }
        }

        String result = postService.toggleLike(postId, userId, username);
        return "liked".equals(result)
                ? Result.successMessage("点赞成功")
                : Result.successMessage("取消点赞");
    }

    @GetMapping("/count/{userId}")
    public Result<Long> getPostCount(@PathVariable("userId") Long userId) {
        Long count = postMapper.selectCount(new LambdaQueryWrapper<Post>().eq(Post::getUserId, userId));
        return Result.success(count == null ? 0 : count);
    }

    /**
     * 极致回源 (S3 备份)
     */
    @PostMapping("/sync-to-cloud")
    public Result<String> syncToCloud(@RequestParam("fileName") String fileName) {
        try {
            File localFile = new File(localPath + fileName);
            if (!localFile.exists()) return Result.fail("本地备份不存在");
            String r2Url = s3Service.restoreToCloud(localFile, fileName);
            return Result.success(r2Url);
        } catch (Exception e) {
            log.error("同步文件到云存储失败: fileName={}", fileName, e);
            return Result.fail("同步失败，请稍后重试");
        }
    }
}
