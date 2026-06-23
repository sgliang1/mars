package com.mars.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication
@EnableAsync
@EnableFeignClients(basePackages = "com.mars.api")
@ComponentScan(basePackages = {"com.mars.user", "com.mars.common", "com.mars.api"})
@MapperScan({"com.mars.user", "com.mars.common.push"})
public class UserApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}