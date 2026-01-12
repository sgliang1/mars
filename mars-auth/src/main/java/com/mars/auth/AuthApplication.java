package com.mars.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
// 👇 1. 这一行必须加！
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
// 👇 2. 这一行必须加！显式告诉 Spring：“去把 common 包里的东西也给我扫进来！”
@ComponentScan(basePackages = {"com.mars.auth", "com.mars.common"})
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}