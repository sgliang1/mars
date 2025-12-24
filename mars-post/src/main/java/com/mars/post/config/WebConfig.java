package com.mars.post.config;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Value("${file.local-path}")
    private String localPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 让浏览器访问 /local-files/xxx.jpg 时，自动去本地磁盘找
        registry.addResourceHandler("/local-files/**")
                .addResourceLocations("file:" + localPath);
    }
}