package com.mars.common.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * 禁用 Nacos 客户端自带的 logback 配置，避免与 Spring Boot 日志系统产生 appender 名称冲突。
 * 通过 EnvironmentPostProcessor 在 Spring 环境准备阶段设置 JVM 系统属性，
 * 所有依赖 mars-common 的模块自动生效，无需在各 Application 类中重复设置。
 */
public class NacosLoggingEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        System.setProperty("nacos.logging.default.config.enabled", "false");
    }
}