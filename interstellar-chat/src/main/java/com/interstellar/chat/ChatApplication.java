package com.interstellar.chat;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.interstellar.api")
@EnableScheduling
// 1. 扫描 common 包，确保能注入 JwtUtil, Result, GlobalExceptionHandler 等通用组件
@ComponentScan(basePackages = {"com.interstellar.chat", "com.interstellar.common", "com.interstellar.api"})
// 2. 扫描 MyBatis Mapper 接口
@MapperScan({"com.interstellar.chat", "com.interstellar.common.push", "com.interstellar.common.outbox"})
public class ChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatApplication.class, args);
    }
}