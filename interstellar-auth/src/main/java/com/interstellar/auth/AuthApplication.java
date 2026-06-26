package com.interstellar.auth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableFeignClients(basePackages = "com.interstellar.api")
@ComponentScan(basePackages = {"com.interstellar.auth", "com.interstellar.common", "com.interstellar.api"})
@MapperScan({"com.interstellar.auth", "com.interstellar.common.push"})
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}