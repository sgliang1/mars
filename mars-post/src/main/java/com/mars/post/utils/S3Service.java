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
                .withPathStyleAccessEnabled(true)
                .withChunkedEncodingDisabled(true)
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey)))
                .build();
    }

    public String uploadFileWithSpecifiedName(MultipartFile file, String fileName) {
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(file.getContentType());
            s3Client.putObject(bucket, fileName, file.getInputStream(), metadata);
            return buildPublicUrl(fileName);
        } catch (IOException e) {
            throw new RuntimeException("图片上传到R2失败", e);
        }
    }

    public String restoreToCloud(File file, String fileName) {
        try {
            s3Client.putObject(bucket, fileName, file);
            return buildPublicUrl(fileName);
        } catch (Exception e) {
            throw new RuntimeException("同步回云端失败", e);
        }
    }

    private String buildPublicUrl(String fileName) {
        String normalizedDomain = publicDomain.endsWith("/") ? publicDomain.substring(0, publicDomain.length() - 1) : publicDomain;
        return normalizedDomain + "/" + fileName;
    }
}
