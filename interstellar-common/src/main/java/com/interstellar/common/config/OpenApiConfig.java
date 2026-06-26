package com.interstellar.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI interstellarOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Interstellar Forum API")
                .version("1.0.0")
                .description("Interstellar Forum 社交论坛平台 API"));
    }
}