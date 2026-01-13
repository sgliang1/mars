package com.mars.post.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mars.common.Result;
import com.mars.post.dto.PostDTO;
import com.mars.post.entity.Post;
import com.mars.post.entity.PostContent;
import com.mars.post.entity.PostImage;
import com.mars.post.entity.PostLike;
import com.mars.post.mapper.PostContentMapper;
import com.mars.post.mapper.PostImageMapper;
import com.mars.post.mapper.PostLikeMapper;
import com.mars.post.mapper.PostMapper;
import com.mars.post.service.PostService;
import com.mars.post.utils.S3Service;
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
@RequestMapping("/post")
public class PostController {

    @Autowired private PostService postService;
    @Autowired private PostMapper postMapper;
    @Autowired private PostContentMapper postContentMapper;
    @Autowired private PostLikeMapper postLikeMapper;
    @Autowired private PostImageMapper postImageMapper;
    @Autowired private S3Service s3Service;

    @Value("${file.local-path}")
    private String localPath;

    /**
     * 发布帖子
     * 修改说明：完全移除 JWT 解析，直接使用网关传过来的 ID 和 用户名
     */
    @PostMapping("/add")
    public Result<String> add(@RequestBody PostDTO postDTO,
                              @RequestHeader("X-User-Id") String userIdStr,
                              @RequestHeader(value = "X-User-Name", required = false) String encodedUsername) {
        try {
            // 1. 获取 ID (网关已鉴权，这里直接转 Long)
            Long userId = Long.parseLong(userIdStr);

            // 2. 获取用户名 (需解码，防止乱码)
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

            // 4. 调用业务 Service 保存 (主表+内容表)
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

            return Result.success("发布成功");
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("发布失败：" + e.getMessage());
        }
    }

    /**
     * 首页列表
     */
    @GetMapping("/list")
    public Result<List<Post>> list(@RequestHeader(value = "X-User-Id", required = false) String userIdStr) {
        // 1. 查帖子
        List<Post> postList = postMapper.selectList(new QueryWrapper<Post>().orderByDesc("create_time"));
        if (postList.isEmpty()) return Result.success(postList);

        // 2. 批量查图片
        List<Long> postIds = postList.stream().map(Post::getId).collect(Collectors.toList());
        List<PostImage> allImages = postImageMapper.selectList(new LambdaQueryWrapper<PostImage>()
                .in(PostImage::getPostId, postIds)
                .orderByAsc(PostImage::getSort));

        // 3. 内存分组
        Map<Long, List<String>> imageMap = new HashMap<>();
        for (PostImage img : allImages) {
            imageMap.computeIfAbsent(img.getPostId(), k -> new ArrayList<>()).add(img.getUrl());
        }

        // 4. 组装数据
        for (Post post : postList) {
            post.setImageList(imageMap.getOrDefault(post.getId(), new ArrayList<>()));
        }

        // 5. 处理点赞状态
        if (userIdStr != null) {
            try {
                Long userId = Long.parseLong(userIdStr);
                List<PostLike> likes = postLikeMapper.selectList(new LambdaQueryWrapper<PostLike>()
                        .eq(PostLike::getUserId, userId)
                        .in(PostLike::getPostId, postIds));
                Set<Long> likedPostIds = likes.stream().map(PostLike::getPostId).collect(Collectors.toSet());

                postList.forEach(p -> {
                    if (likedPostIds.contains(p.getId())) p.setLiked(true);
                });
            } catch (Exception e) {}
        }

        return Result.success(postList);
    }

    /**
     * 帖子详情
     */
    @GetMapping("/detail/{id}")
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
        data.put("username", post.getUsername());
        data.put("userId", post.getUserId());
        data.put("imageList", imageUrls);
        data.put("likeCount", post.getLikeCount());
        data.put("commentCount", post.getCommentCount());
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
     * 点赞接口
     */
    @PostMapping("/like/{postId}")
    public Result like(@PathVariable("postId") Long postId, @RequestHeader(value = "X-User-Id", required = false) String userIdStr) {
        if (userIdStr == null) return Result.fail("请先登录");
        Long userId = Long.parseLong(userIdStr);

        PostLike existingLike = postLikeMapper.selectOne(new LambdaQueryWrapper<PostLike>()
                .eq(PostLike::getPostId, postId)
                .eq(PostLike::getUserId, userId));

        Post post = postMapper.selectById(postId);
        if (post == null) return Result.fail("帖子不存在");
        int currentCount = post.getLikeCount() == null ? 0 : post.getLikeCount();

        if (existingLike != null) {
            postLikeMapper.deleteById(existingLike.getId());
            if (currentCount > 0) {
                post.setLikeCount(currentCount - 1);
                postMapper.updateById(post);
            }
            return Result.success("取消点赞");
        } else {
            PostLike like = new PostLike();
            like.setPostId(postId);
            like.setUserId(userId);
            like.setCreateTime(LocalDateTime.now());
            postLikeMapper.insert(like);
            post.setLikeCount(currentCount + 1);
            postMapper.updateById(post);
            return Result.success("点赞成功");
        }
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