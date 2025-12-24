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
import java.time.LocalDate;           // ✅ 新增
import java.time.format.DateTimeFormatter; // ✅ 新增
import java.util.UUID;

@RestController
@RequestMapping("/file")
public class FileController {

    @Autowired
    private S3Service s3Service;

    @Value("${file.local-path}")
    private String localPath;

    @PostMapping("/upload")
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        try {
            // 1. 生成文件名后缀
            String originalFilename = file.getOriginalFilename();
            String suffix = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
            }

            // =========================================================
            // ✅ 核心修改开始：构建分层路径 (例如: post/2025/12/24)
            // =========================================================
            String datePath = "post/" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));

            // 最终文件名：post/2025/12/24/uuid-xxx.jpg
            // 注意：S3/R2 使用 '/' 作为目录分隔符，本地路径拼接时 Java 也能正确识别 '/'
            String fileName = datePath + "/" + UUID.randomUUID().toString() + suffix;
            // =========================================================

            // 2. 优先上传到 Cloudflare R2 (R2 会自动根据 '/' 创建文件夹结构)
            String r2Url = s3Service.uploadFileWithSpecifiedName(file, fileName);

            // 3. 本地备份
            File localDest = new File(localPath + fileName);

            // ✅ 核心修改：确保本地多级父目录存在 (例如 E:/mars_uploads/post/2025/12/24/)
            if (!localDest.getParentFile().exists()) {
                localDest.getParentFile().mkdirs();
            }

            // 使用 copy 重新读取流写入本地
            try (InputStream in = file.getInputStream();
                 FileOutputStream out = new FileOutputStream(localDest)) {
                FileCopyUtils.copy(in, out);
            } catch (Exception ex) {
                System.err.println("本地备份异常(不影响主流程): " + ex.getMessage());
            }

            // 4. 返回结果
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
}