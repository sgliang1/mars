package com.mars.post.controller;

import com.mars.common.Result;
import com.mars.post.utils.S3Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/file")
public class FileController {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp"
    );

    @Autowired
    private S3Service s3Service;

    @Value("${file.local-path}")
    private String localPath;

    @PostMapping("/upload")
    public Result<String> upload(@RequestParam("file") MultipartFile file,
                                 @RequestParam(value = "scene", defaultValue = "post") String scene,
                                 @RequestHeader(value = "X-User-Id", required = false) String userIdStr) {
        try {
            validateFile(file);

            String fileName = buildStorageKey(file, scene, userIdStr);
            String r2Url = s3Service.uploadFileWithSpecifiedName(file, fileName);

            Path localTargetPath = resolveLocalTarget(fileName);
            File localDest = localTargetPath.toFile();
            File parent = localDest.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            try (InputStream in = file.getInputStream();
                 FileOutputStream out = new FileOutputStream(localDest)) {
                FileCopyUtils.copy(in, out);
            } catch (Exception ex) {
                System.err.println("本地备份异常(不影响主流程): " + ex.getMessage());
            }

            Result<String> result = new Result<>();
            result.setCode(200);
            result.setMsg("上传成功");
            result.setData(r2Url);
            return result;

        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("上传失败: " + e.getMessage());
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }

        String suffix = extractSuffix(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(suffix)) {
            throw new IllegalArgumentException("不支持的图片格式");
        }

        String contentType = file.getContentType();
        if (contentType != null
                && !contentType.isBlank()
                && !"application/octet-stream".equalsIgnoreCase(contentType)
                && !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new IllegalArgumentException("仅支持图片上传");
        }
    }

    private String buildStorageKey(MultipartFile file, String scene, String userIdStr) {
        String safeScene = sanitizeScene(scene);
        String safeUserId = sanitizeUserId(userIdStr);
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String suffix = extractSuffix(file.getOriginalFilename());
        return safeScene + "/" + safeUserId + "/" + datePath + "/" + UUID.randomUUID() + suffix;
    }

    private Path resolveLocalTarget(String fileName) {
        String[] segments = fileName.split("/");
        return Paths.get(localPath, segments);
    }

    private String sanitizeScene(String scene) {
        if (scene == null || scene.isBlank()) {
            return "post";
        }
        return scene.replaceAll("[^a-zA-Z0-9_-]", "").toLowerCase(Locale.ROOT);
    }

    private String sanitizeUserId(String userIdStr) {
        if (userIdStr == null || userIdStr.isBlank()) {
            return "anonymous";
        }
        String sanitized = userIdStr.replaceAll("[^0-9]", "");
        return sanitized.isBlank() ? "anonymous" : sanitized;
    }

    private String extractSuffix(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new IllegalArgumentException("文件缺少后缀");
        }
        return originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase(Locale.ROOT);
    }
}
