package com.mars.post.utils;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;

@Service
public class S3Service {

    // ✅ 修改：改为读取 r2.a 的配置
    // 在 S3Service.java 中修改这几行
    @Value("${r2.a.access-key}")
    private String accessKey;

    @Value("${r2.a.secret-key}")
    private String secretKey;

    @Value("${r2.a.endpoint}")
    private String endpoint;

    @Value("${r2.a.bucket}")
    private String bucket;

    @Value("${r2.a.public-domain}")
    private String publicDomain;

    private AmazonS3 s3Client;

    @PostConstruct
    public void init() {
        s3Client = AmazonS3ClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpoint, "auto"))
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey)))
                .build();
    }

    // ✅ 修改：新增这个方法，供 FileController 调用，确保文件名与本地一致
    public String uploadFileWithSpecifiedName(MultipartFile file, String fileName) {
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(file.getContentType());
            s3Client.putObject(bucket, fileName, file.getInputStream(), metadata);
            // 返回 R2 的访问链接
            return publicDomain + "/" + fileName;
        } catch (IOException e) {
            throw new RuntimeException("图片上传到R2失败", e);
        }
    }
    public String restoreToCloud(File file, String fileName) {
        try {
            // 直接把本地 File 上传回 R2
            s3Client.putObject(bucket, fileName, file);
            return publicDomain + "/" + fileName;
        } catch (Exception e) {
            throw new RuntimeException("同步回云端失败", e);
        }
    }
}