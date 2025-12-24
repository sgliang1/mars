package com.mars.post.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mars.common.Result;
import com.mars.common.util.JwtUtil;
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
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.File;
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
    @Autowired private PostImageMapper postImageMapper; // ✅ 新增
    @Autowired private S3Service s3Service;

    @Value("${file.local-path}")
    private String localPath;

    /**
     * 发布帖子 (重构：存入独立图片表)
     */
    @PostMapping("/add")
    public Result<String> add(@RequestBody PostDTO postDTO, @RequestHeader("Authorization") String token) {
        try {
            String realToken = token.substring(7);
            Claims claims = JwtUtil.parseToken(realToken);
            Long userId = Long.valueOf(claims.get("userId").toString());
            String username = claims.get("username", String.class);

            // 1. 保存帖子主表
            Post post = new Post();
            post.setUserId(userId);
            post.setUsername(username);
            post.setTitle(postDTO.getTitle());
            post.setLikeCount(0);
            post.setCommentCount(0);
            // Service 负责保存 post 和 post_content
            postService.publish(post, postDTO.getContent());

            // 2. ✅ 保存图片到独立表 (解析逗号分隔的字符串)
            if (postDTO.getImages() != null && !postDTO.getImages().isEmpty()) {
                String[] urls = postDTO.getImages().split(",");
                for (int i = 0; i < urls.length; i++) {
                    PostImage img = new PostImage();
                    img.setPostId(post.getId()); // ID 由 MybatisPlus 回填
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
     * 首页列表 (重构：批量组装图片)
     */
    @GetMapping("/list")
    public Result<List<Post>> list(@RequestHeader(value = "X-User-Id", required = false) String userIdStr) {
        // 1. 查帖子
        List<Post> postList = postMapper.selectList(new QueryWrapper<Post>().orderByDesc("create_time"));
        if (postList.isEmpty()) return Result.success(postList);

        // 2. ✅ 批量查图片 (解决 N+1 性能问题)
        List<Long> postIds = postList.stream().map(Post::getId).collect(Collectors.toList());
        List<PostImage> allImages = postImageMapper.selectList(new LambdaQueryWrapper<PostImage>()
                .in(PostImage::getPostId, postIds)
                .orderByAsc(PostImage::getSort));

        // 内存分组：Map<PostId, List<Url>>
        Map<Long, List<String>> imageMap = new HashMap<>();
        for (PostImage img : allImages) {
            imageMap.computeIfAbsent(img.getPostId(), k -> new ArrayList<>()).add(img.getUrl());
        }

        // 3. 组装回 Post 对象 (Post 实体需添加 private List<String> imageList 字段)
        for (Post post : postList) {
            post.setImageList(imageMap.getOrDefault(post.getId(), new ArrayList<>()));
        }

        // 4. 处理点赞状态
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
     * 帖子详情 (重构：返回图片数组)
     */
    @GetMapping("/detail/{id}")
    public Result<Map<String, Object>> getDetail(@PathVariable("id") Long id,
                                                 @RequestHeader(value = "X-User-Id", required = false) String userIdStr) {
        Post post = postMapper.selectById(id);
        if (post == null) return Result.fail("内容不存在");
        PostContent content = postContentMapper.selectById(id);

        // ✅ 查图片
        List<PostImage> images = postImageMapper.selectList(new LambdaQueryWrapper<PostImage>()
                .eq(PostImage::getPostId, id)
                .orderByAsc(PostImage::getSort));
        List<String> imageUrls = images.stream().map(PostImage::getUrl).collect(Collectors.toList());

        Map<String, Object> data = new HashMap<>();
        data.put("id", post.getId());
        data.put("username", post.getUsername());
        data.put("userId", post.getUserId());
        // ✅ 直接返回 List，前端无需 split
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
     * 点赞接口 (保持不变)
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
     * 极致回源 (保持不变)
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