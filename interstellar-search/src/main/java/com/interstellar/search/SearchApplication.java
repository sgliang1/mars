package com.interstellar.search;

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
@ComponentScan(basePackages = {"com.interstellar.search", "com.interstellar.common"})
@MapperScan(value = {"com.interstellar.search.domain.post", "com.interstellar.common.push"}, annotationClass = org.apache.ibatis.annotations.Mapper.class)
public class SearchApplication {
    public static void main(String[] args) {
        SpringApplication.run(SearchApplication.class, args);
    }
}
