package com.interstellar.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication
@EnableAsync
@EnableFeignClients(basePackages = "com.interstellar.api")
@ComponentScan(basePackages = {"com.interstellar.user", "com.interstellar.common", "com.interstellar.api"})
@MapperScan({"com.interstellar.user", "com.interstellar.common.push"})
public class UserApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}