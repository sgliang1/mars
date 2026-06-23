package com.mars.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI marsOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Mars Forum API")
                .version("1.0.0")
                .description("Mars Forum 社交论坛平台 API"));
    }
}