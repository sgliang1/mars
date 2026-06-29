package com.interstellar.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.interstellar.admin", "com.interstellar.common"})
@MapperScan({"com.interstellar.admin.domain", "com.interstellar.common.push"})
@EnableFeignClients(basePackages = "com.interstellar.api")
public class AdminApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}