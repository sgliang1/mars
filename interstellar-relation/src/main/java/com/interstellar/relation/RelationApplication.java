package com.interstellar.relation;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
@EnableAsync
@EnableFeignClients(basePackages = "com.interstellar.api")
@ComponentScan(basePackages = {"com.interstellar.relation", "com.interstellar.common", "com.interstellar.api"})
@MapperScan(value = {"com.interstellar.relation", "com.interstellar.common.push", "com.interstellar.common.outbox"}, annotationClass = org.apache.ibatis.annotations.Mapper.class)
public class RelationApplication {

    public static void main(String[] args) {
        SpringApplication.run(RelationApplication.class, args);
    }
}
