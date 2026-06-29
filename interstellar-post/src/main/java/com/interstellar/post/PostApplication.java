package com.interstellar.post;

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
@ComponentScan(basePackages = {"com.interstellar.post", "com.interstellar.common"})
@MapperScan(value = {"com.interstellar.post.domain.*", "com.interstellar.common.push", "com.interstellar.common.outbox"}, annotationClass = org.apache.ibatis.annotations.Mapper.class)
public class PostApplication {
    public static void main(String[] args) {
        SpringApplication.run(PostApplication.class, args);
    }
}