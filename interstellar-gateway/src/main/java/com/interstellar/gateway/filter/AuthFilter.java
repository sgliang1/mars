package com.interstellar.gateway.filter;

import com.interstellar.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 网关全局鉴权过滤器
 * 1. 白名单路径放行
 * 2. JWT 验签，解析用户信息传给下游
 * 3. 支持 Authorization Header 和 WebSocket query token 两种方式
 */
@Component
public class AuthFilter implements GlobalFilter, Ordered {

    @Value("${gateway.auth.whitelist:}")
    private String whitelistConfig;

    @Value("${gateway.cors.allowed-origins:http://localhost:4444,http://localhost:3000}")
    private String allowedOrigins;

    private volatile Set<String> whitelistPaths;
    private volatile Set<String> allowedOriginSet;

    /**
     * 获取白名单集合（惰性解析，避免每次请求都 split）
     */
    private Set<String> getWhitelistPaths() {
        Set<String> paths = this.whitelistPaths;
        if (paths == null) {
            synchronized (this) {
                paths = this.whitelistPaths;
                if (paths == null) {
                    paths = Arrays.stream(whitelistConfig.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toUnmodifiableSet());
                    this.whitelistPaths = paths;
                }
            }
        }
        return paths;
    }

    /**
     * 获取允许的 CORS Origin 集合（惰性解析）
     */
    private Set<String> getAllowedOriginSet() {
        Set<String> origins = this.allowedOriginSet;
        if (origins == null) {
            synchronized (this) {
                origins = this.allowedOriginSet;
                if (origins == null) {
                    origins = Arrays.stream(allowedOrigins.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toUnmodifiableSet());
                    this.allowedOriginSet = origins;
                }
            }
        }
        return origins;
    }

    /**
     * 规范化路径：去除尾部斜杠、合并连续斜杠，防止路径变体绕过白名单
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

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // OPTIONS 预检请求直接放行
        if (request.getMethod() == org.springframework.http.HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }

        String path = normalizePath(request.getURI().getPath());

        // 白名单放行
        if (getWhitelistPaths().contains(path)) {
            return chain.filter(exchange);
        }

        String token = null;

        // 优先从 Authorization Header 获取
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }

        // WebSocket 专用：从 query 参数获取 token
        if (token == null) {
            token = request.getQueryParams().getFirst("token");
        }

        if (token == null || token.isEmpty()) {
            return unAuthorized(exchange);
        }

        try {
            Claims claims = JwtUtil.parseToken(token);

            // 安全地提取 claims，缺失时返回 401 而不是 NPE
            Object userIdObj = claims.get("userId");
            Object usernameObj = claims.get("username");
            if (userIdObj == null || usernameObj == null) {
                return unAuthorized(exchange);
            }

            String userId = userIdObj.toString();
            String username = usernameObj.toString();
            String role = claims.get("role") != null ? claims.get("role").toString() : "user";

            String encodedUsername = URLEncoder.encode(username, StandardCharsets.UTF_8);

            ServerHttpRequest mutatedRequest = request.mutate()
                    .header("X-User-Id", userId)
                    .header("X-User-Name", encodedUsername)
                    .header("X-User-Role", role)
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (Exception e) {
            // Token 解析失败（过期、篡改等）
            return unAuthorized(exchange);
        }
    }

    /**
     * 返回 401 响应，附加 CORS 头（使用白名单域，不反射 Origin）
     */
    private Mono<Void> unAuthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);

        // 用配置的白名单域替代反射 Origin，防止注入任意 Origin
        String origin = exchange.getRequest().getHeaders().getOrigin();
        if (origin != null && getAllowedOriginSet().contains(origin)) {
            response.getHeaders().add("Access-Control-Allow-Origin", origin);
            response.getHeaders().add("Access-Control-Allow-Credentials", "true");
        }

        return response.setComplete();
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
