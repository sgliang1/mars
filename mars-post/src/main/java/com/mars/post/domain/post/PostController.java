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
import com.mars.post.infrastructure.file.S3Service;
import com.mars.post.domain.notification.NotificationHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/posts")
public class PostController {

    @Autowired private PostService postService;
    @Autowired private PostMapper postMapper;
    @Autowired private PostContentMapper postContentMapper;
    @Autowired private PostLikeMapper postLikeMapper;
    @Autowired private PostImageMapper postImageMapper;
    @Autowired private S3Service s3Service;
    @Autowired private NotificationHelper notificationHelper;

    @Value("${file.local-path}")
    private String localPath;

    /**
     * 发布帖子
     * 修改说明：完全移�?JWT 解析，直接使用网关传过来�?ID �?用户�?
     */
    @PostMapping("")
    public Result<String> add(@RequestBody PostDTO postDTO,
                              @RequestHeader("X-User-Id") String userIdStr,
                              @RequestHeader(value = "X-User-Name", required = false) String encodedUsername) {
        try {
            // 1. 获取 ID (网关已鉴权，这里直接�?Long)
            Long userId = Long.parseLong(userIdStr);

            // 2. 获取用户�?(需解码，防止乱�?
            String username = "匿名用户";
            if (encodedUsername != null) {
                username = URLDecoder.decode(encodedUsername, StandardCharsets.UTF_8.name());
            }

            // 3. 组装实体
            Post post = new Post();
            post.setUserId(userId);
            post.setUsername(username);
            post.setTitle(postDTO.getTitle());
            post.setLikeCount(0);
            post.setCommentCount(0);
            post.setShareCount(0);

            // 4. 调用业务 Service 保存 (主表+内容�?
            postService.publish(post, postDTO.getContent());

            // 5. 保存图片
            if (postDTO.getImages() != null && !postDTO.getImages().isEmpty()) {
                String[] urls = postDTO.getImages().split(",");
                for (int i = 0; i < urls.length; i++) {
                    PostImage img = new PostImage();
                    img.setPostId(post.getId()); // ID �?MyBatis Plus 回填
                    img.setUrl(urls[i].trim());
                    img.setSort(i);
                    postImageMapper.insert(img);
                }
            }

            return Result.successMessage("发布成功");
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("发布失败：" + e.getMessage());
        }
    }

    /**
     * 首页列表
     */
    @GetMapping("")
    public Result<List<Post>> list(@RequestHeader(value = "X-User-Id", required = false) String userIdStr,
                                   @RequestParam(value = "page", defaultValue = "1") int page,
                                   @RequestParam(value = "size", defaultValue = "20") int size) {
        Page<Post> pageParam = new Page<>(page, size);
        Page<Post> result = postMapper.selectPage(pageParam, new QueryWrapper<Post>().orderByDesc("create_time"));
        return Result.success(attachPostExtras(result.getRecords(), userIdStr));
    }

    /**
     * 当前用户的帖子列�?     */
    @GetMapping("/mine")
    public Result<List<Post>> mine(@RequestHeader("X-User-Id") String userIdStr,
                                   @RequestParam(value = "page", defaultValue = "1") int page,
                                   @RequestParam(value = "size", defaultValue = "20") int size) {
        Long userId = Long.parseLong(userIdStr);
        Page<Post> pageParam = new Page<>(page, size);
        Page<Post> result = postMapper.selectPage(pageParam, new QueryWrapper<Post>()
                .eq("user_id", userId)
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
            } catch (Exception e) {}
        }

        return postList;
    }

    /**
     * 帖子详情
     */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> getDetail(@PathVariable("id") Long id,
                                                 @RequestHeader(value = "X-User-Id", required = false) String userIdStr) {
        Post post = postMapper.selectById(id);
        if (post == null) return Result.fail("内容不存在");
        PostContent content = postContentMapper.selectById(id);

        List<PostImage> images = postImageMapper.selectList(new LambdaQueryWrapper<PostImage>()
                .eq(PostImage::getPostId, id)
                .orderByAsc(PostImage::getSort));
        List<String> imageUrls = images.stream().map(PostImage::getUrl).collect(Collectors.toList());

        Map<String, Object> data = new HashMap<>();
        data.put("id", post.getId());
        data.put("title", post.getTitle());
        data.put("username", post.getUsername());
        data.put("userId", post.getUserId());
        data.put("imageList", imageUrls);
        data.put("likeCount", post.getLikeCount());
        data.put("commentCount", post.getCommentCount());
        data.put("shareCount", post.getShareCount());
        data.put("createTime", post.getCreateTime());
        data.put("content", content != null ? content.getContent() : "");

        boolean isLiked = false;
        if (userIdStr != null) {
            try {
                Long userId = Long.parseLong(userIdStr);
                Long count = postLikeMapper.selectCount(new LambdaQueryWrapper<PostLike>()
                        .eq(PostLike::getPostId, id).eq(PostLike::getUserId, userId));
                isLiked = count > 0;
            } catch (Exception e) {}
        }
        data.put("isLiked", isLiked);
        return Result.success(data);
    }

    /**
     * 更新帖子
     */
    @PutMapping("/{id}")
    public Result<String> update(@PathVariable("id") Long id,
                                 @RequestBody PostDTO postDTO,
                                 @RequestHeader("X-User-Id") String userIdStr) {
        try {
            Long userId = Long.parseLong(userIdStr);
            boolean updated = postService.updatePost(id, userId, postDTO);
            if (!updated) {
                return Result.fail("帖子不存在或无权限修改");
            }
            return Result.successMessage("更新成功");
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("更新失败：" + e.getMessage());
        }
    }

    /**
     * 删除帖子
     */
    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable("id") Long id,
                                 @RequestHeader("X-User-Id") String userIdStr) {
        try {
            Long userId = Long.parseLong(userIdStr);
            boolean deleted = postService.deletePost(id, userId);
            if (!deleted) {
                return Result.fail("帖子不存在或无权限删除");
            }
            return Result.successMessage("删除成功");
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("删除失败：" + e.getMessage());
        }
    }

    /**
     * 点赞接口
     */
    @PostMapping("/{postId}/like")
    public Result like(@PathVariable("postId") Long postId,
                       @RequestHeader(value = "X-User-Id", required = false) String userIdStr,
                       @RequestHeader(value = "X-User-Name", required = false) String encodedUsername) {
        if (userIdStr == null) return Result.fail("请先登录");
        Long userId = Long.parseLong(userIdStr);

        String username = "匿名用户";
        if (encodedUsername != null) {
            try {
                username = URLDecoder.decode(encodedUsername, StandardCharsets.UTF_8.name());
            } catch (Exception ignored) {}
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
            return Result.fail("同步失败：" + e.getMessage());
        }
    }
}
