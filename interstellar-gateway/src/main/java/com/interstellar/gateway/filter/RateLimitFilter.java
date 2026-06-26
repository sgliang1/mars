package com.interstellar.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interstellar.common.Result;
import com.interstellar.gateway.config.RateLimitProperties;
import com.interstellar.gateway.config.RateLimitProperties.RateLimit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 网关全局限流过滤器
 * <p>
 * 基于 Redis 滑动窗口的两级限流：
 * <ul>
 *   <li>IP 维度：对所有请求生效，拦截 DDoS / 匿名滥用</li>
 *   <li>用户维度：对已登录用户生效，集成信用分动态调整限额</li>
 * </ul>
 * 读写操作使用不同阈值，写操作更严格。
 * <p>
 * Redis 不可用时降级放行（fail-open），防止限流组件故障拖垮网关。
 */
@Slf4j
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final String RATE_LIMIT_SCRIPT_PATH = "lua/sliding_window_rate_limit.lua";

    private static final DefaultRedisScript<List> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>();

    private static final Set<HttpMethod> WRITE_METHODS = Set.of(
            HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE
    );

    static {
        RATE_LIMIT_SCRIPT.setScriptSource(new ResourceScriptSource(
                new ClassPathResource(RATE_LIMIT_SCRIPT_PATH)));
        RATE_LIMIT_SCRIPT.setResultType(List.class);
    }

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RateLimitProperties properties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public int getOrder() {
        return 1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // OPTIONS 预检请求不参与限流（与 AuthFilter 行为一致）
        if (request.getMethod() == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }

        // 总开关
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }

        // 检查跳过路径
        String path = normalizePath(request.getURI().getPath());
        if (properties.getSkipPaths().contains(path)) {
            return chain.filter(exchange);
        }

        boolean isWrite = WRITE_METHODS.contains(request.getMethod());
        String clientIp = extractClientIp(request);
        String userId = request.getHeaders().getFirst("X-User-Id");

        return Mono.fromCallable(() -> doRateLimit(clientIp, userId, isWrite))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(result -> {
                    if (result == null) {
                        // Redis 异常，降级放行
                        return chain.filter(exchange);
                    }

                    // 添加限流响应头
                    addRateLimitHeaders(exchange, result);

                    if (result.rejected) {
                        return tooManyRequests(exchange, result);
                    }

                    return chain.filter(exchange);
                });
    }

    /**
     * 执行两级限流检查（在 boundedElastic 线程中调用，避免阻塞 Netty 事件循环）
     */
    private RateLimitResult doRateLimit(String clientIp, String userId, boolean isWrite) {
        try {
            // --- 第一级：IP 维度限流 ---
            String ipKey = "interstellar:rl:ip:" + clientIp + ":" + (isWrite ? "w" : "r");
            RateLimit ipLimit = isWrite ? properties.getIpWrite() : properties.getIpRead();
            RateLimitResult ipResult = executeLua(ipKey, ipLimit);

            if (ipResult.rejected) {
                ipResult.dimension = "ip";
                return ipResult;
            }

            // --- 第二级：用户维度限流（仅已登录用户） ---
            if (userId != null && !userId.isEmpty()) {
                // 读取信用分，动态调整限额
                RateLimit effectiveLimit = isWrite ? properties.getUserWrite() : properties.getUserRead();
                effectiveLimit = adjustLimitByCredit(userId, effectiveLimit);

                String userKey = "interstellar:rl:user:" + userId + ":" + (isWrite ? "w" : "r");
                RateLimitResult userResult = executeLua(userKey, effectiveLimit);

                if (userResult.rejected) {
                    userResult.dimension = "user";
                    userResult.limit = effectiveLimit.getLimit();
                    return userResult;
                }

                // 以用户维度的结果作为最终结果（用于响应头）
                userResult.dimension = "user";
                userResult.limit = effectiveLimit.getLimit();
                return userResult;
            }

            // 未登录用户，返回 IP 维度结果
            ipResult.dimension = "ip";
            return ipResult;

        } catch (Exception e) {
            // Redis 不可用，降级放行
            log.warn("限流 Redis 调用异常，降级放行: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 根据信用分调整用户限额
     * 信用分低于阈值时，限额按 creditMultiplier 缩减
     * 信用分不存在时按正常限额处理（fail-open）
     */
    private RateLimit adjustLimitByCredit(String userId, RateLimit base) {
        try {
            String creditKey = "interstellar:user:credit:" + userId;
            String creditStr = stringRedisTemplate.opsForValue().get(creditKey);

            if (creditStr == null || creditStr.isEmpty()) {
                // 信用分未缓存，按正常限额
                return base;
            }

            int creditScore = Integer.parseInt(creditStr);
            if (creditScore < properties.getCreditThreshold()) {
                int adjustedLimit = Math.max(1, (int) (base.getLimit() * properties.getCreditMultiplier()));
                log.debug("用户 {} 信用分={}，限额调整: {} -> {}", userId, creditScore,
                        base.getLimit(), adjustedLimit);
                return new RateLimit(adjustedLimit, base.getWindowSeconds());
            }

            return base;
        } catch (Exception e) {
            // 信用分读取失败，按正常限额
            log.warn("读取用户 {} 信用分失败，使用正常限额: {}", userId, e.getMessage());
            return base;
        }
    }

    /**
     * 执行 Redis Lua 滑动窗口脚本
     */
    @SuppressWarnings("unchecked")
    private RateLimitResult executeLua(String key, RateLimit limit) {
        long now = System.currentTimeMillis();
        long windowMs = limit.getWindowSeconds() * 1000L;
        String uniqueId = now + ":" + UUID.randomUUID().toString().substring(0, 8);

        List<Long> result = stringRedisTemplate.execute(
                RATE_LIMIT_SCRIPT,
                List.of(key),
                String.valueOf(now),
                String.valueOf(windowMs),
                String.valueOf(limit.getLimit()),
                uniqueId
        );

        long count = result.get(0);
        long retryAfter = result.get(1);

        RateLimitResult rlResult = new RateLimitResult();
        rlResult.count = (int) count;
        rlResult.limit = limit.getLimit();
        rlResult.retryAfter = (int) retryAfter;
        rlResult.rejected = retryAfter > 0;
        rlResult.windowSeconds = limit.getWindowSeconds();
        return rlResult;
    }

    /**
     * 返回 429 Too Many Requests 响应
     */
    private Mono<Void> tooManyRequests(ServerWebExchange exchange, RateLimitResult result) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().add("Retry-After", String.valueOf(result.retryAfter));
        response.getHeaders().add("Access-Control-Allow-Origin",
                exchange.getRequest().getHeaders().getOrigin());
        response.getHeaders().add("Access-Control-Allow-Credentials", "true");

        try {
            byte[] body = objectMapper.writeValueAsBytes(
                    Result.fail(429, "请求过于频繁，请稍后再试"));
            return response.writeWith(
                    Mono.just(response.bufferFactory().wrap(body)));
        } catch (Exception e) {
            return response.setComplete();
        }
    }

    /**
     * 添加限流响应头（无论是否超限都添加，让客户端感知配额）
     */
    private void addRateLimitHeaders(ServerWebExchange exchange, RateLimitResult result) {
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().add("X-RateLimit-Limit", String.valueOf(result.limit));
        response.getHeaders().add("X-RateLimit-Remaining",
                String.valueOf(Math.max(0, result.limit - result.count)));
        response.getHeaders().add("X-RateLimit-Reset",
                String.valueOf((System.currentTimeMillis() / 1000) + result.windowSeconds));
    }

    /**
     * 提取客户端真实 IP
     * 优先从 X-Forwarded-For 获取（反向代理场景），兜底使用 RemoteAddress
     */
    private String extractClientIp(ServerHttpRequest request) {
        String xff = request.getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            // 取第一个 IP（最左边是原始客户端）
            return xff.split(",")[0].trim();
        }

        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null) {
            InetAddress address = remoteAddress.getAddress();
            if (address != null) {
                return address.getHostAddress();
            }
        }
        return "unknown";
    }

    /**
     * 规范化路径（与 AuthFilter 保持一致）
     */
    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        String normalized = path.replaceAll("/+", "/");
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    /**
     * 限流检查结果
     */
    private static class RateLimitResult {
        int count;
        int limit;
        int retryAfter;
        int windowSeconds;
        boolean rejected;
        String dimension;
    }
}
