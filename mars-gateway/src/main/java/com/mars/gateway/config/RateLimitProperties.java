package com.mars.gateway.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 网关限流配置属性
 * 通过 application.properties 或 Nacos 配置中心进行外部化配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "gateway.rate-limit")
public class RateLimitProperties {

    /** 总开关，设为 false 可关闭所有限流 */
    private boolean enabled = true;

    /** IP 维度 - 读操作限流（GET/HEAD/OPTIONS） */
    private RateLimit ipRead = new RateLimit(200, 60);

    /** IP 维度 - 写操作限流（POST/PUT/PATCH/DELETE） */
    private RateLimit ipWrite = new RateLimit(50, 60);

    /** 用户维度 - 读操作限流 */
    private RateLimit userRead = new RateLimit(300, 60);

    /** 用户维度 - 写操作限流 */
    private RateLimit userWrite = new RateLimit(80, 60);

    /** 信用分阈值，低于此值的用户使用降额限额 */
    private int creditThreshold = 80;

    /** 低信用用户限额倍率（0.3 = 正常额度的 30%） */
    private double creditMultiplier = 0.3;

    /** 跳过限流的路径集合（如健康检查） */
    private Set<String> skipPaths = Set.of();

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RateLimit {
        /** 窗口内最大请求数 */
        private int limit = 200;
        /** 滑动窗口大小（秒） */
        private int windowSeconds = 60;
    }
}
