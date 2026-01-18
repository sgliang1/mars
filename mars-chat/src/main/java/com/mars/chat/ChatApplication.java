package com.mars.chat;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableDiscoveryClient
// 1. 扫描 common 包，确保能注入 JwtUtil, Result, GlobalExceptionHandler 等通用组件
@ComponentScan(basePackages = {"com.mars.chat", "com.mars.common"})
// 2. 扫描 MyBatis Mapper 接口
@MapperScan("com.mars.chat.mapper")
public class ChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatApplication.class, args);
    }
}