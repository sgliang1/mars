package com.interstellar.interaction;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
@ComponentScan(basePackages = {"com.interstellar.interaction", "com.interstellar.common"})
@MapperScan(value = {"com.interstellar.interaction.domain", "com.interstellar.common.push", "com.interstellar.common.outbox"}, annotationClass = org.apache.ibatis.annotations.Mapper.class)
public class InteractionApplication {
    public static void main(String[] args) {
        SpringApplication.run(InteractionApplication.class, args);
    }
}
