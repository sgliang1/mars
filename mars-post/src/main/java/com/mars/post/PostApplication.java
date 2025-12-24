package com.mars.post;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
@MapperScan("com.mars.post.mapper")
public class PostApplication {
    public static void main(String[] args) { SpringApplication.run(PostApplication.class, args); }
}