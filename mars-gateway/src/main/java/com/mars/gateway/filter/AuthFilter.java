package com.mars.gateway.filter;

import com.mars.common.util.JwtUtil;
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
 * 修改说明：
 * 1. 移除了重复的硬编码密钥逻辑
 * 2. 统一调用 JwtUtil.parseToken 进行验签
 * 3. 增加了中文用户名的 URLEncode 处理，防止乱码
 * 4. 新增 WebSocket 鉴权支持（支持从 URL 参数 token 读取）
 */
@Component
public class AuthFilter implements GlobalFilter, Ordered {

    /**
     * 白名单路径列表，从配置文件读取，支持热更新
     * 格式：逗号分隔的精确路径，如 /mars-auth/login,/mars-auth/register
     */
    @Value("${gateway.auth.whitelist:}")
    private String whitelistConfig;

    private volatile Set<String> whitelistPaths;

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
     * 规范化路径：去除尾部斜杠（根路径 "/" 除外）、合并连续斜杠
     * 防止 /mars-auth/login/ 或 /mars-auth//login 等变体绕过精确匹配
     */
    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        // 合并连续斜杠：// -> /
        String normalized = path.replaceAll("/+", "/");
        // 去除尾部斜杠（保留根路径）
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // 0. OPTIONS 预检请求直接放行，确保 CorsWebFilter 能正常注入 CORS 响应头
        if (request.getMethod() == org.springframework.http.HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }

        // 规范化路径，防止尾部斜杠、连续斜杠等变体绕过精确匹配
        String path = normalizePath(request.getURI().getPath());

        // 1. 白名单放行：公开接口不需要 Token（使用精确匹配，防止子串绕过）
        if (getWhitelistPaths().contains(path)) {
            return chain.filter(exchange);
        }

        String token = null;

        // 2. 优先尝试从 Header 获取 (HTTP 接口标准)
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7); // 去掉 "Bearer "
        }

        // 3. ⚠️ 新增：如果 Header 没有，尝试从 Query 参数获取 (WebSocket 专用)
        // 例如：ws://localhost/mars-chat/ws?token=xxxxx
        if (token == null) {
            token = request.getQueryParams().getFirst("token");
        }

        // 4. 如果都拿不到，或者为空，拒绝
        if (token == null || token.isEmpty()) {
            return unAuthorized(exchange);
        }

        // 5. 验签逻辑 (与之前保持一致)
        try {
            // 直接调用 JwtUtil 解析，逻辑统一
            Claims claims = JwtUtil.parseToken(token);

            String userId = claims.get("userId").toString();
            String username = claims.get("username").toString();
            String role = claims.get("role") != null ? claims.get("role").toString() : "user";

            // 对用户名进行 URL 编码，防止 Header 中传递中文导致乱码或报错
            String encodedUsername = URLEncoder.encode(username, StandardCharsets.UTF_8.name());

            // 将用户信息放入 Header 传递给下游微服务
            ServerHttpRequest mutatedRequest = request.mutate()
                    .header("X-User-Id", userId)
                    .header("X-User-Name", encodedUsername)
                    .header("X-User-Role", role)
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (Exception e) {
            // Token 解析失败（过期、篡改、密钥不匹配等）
            return unAuthorized(exchange);
        }
    }

    /**
     * 返回 401 未授权响应，同时附加 CORS 头以防止浏览器拦截错误响应
     */
    private Mono<Void> unAuthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Access-Control-Allow-Origin",
                exchange.getRequest().getHeaders().getOrigin());
        response.getHeaders().add("Access-Control-Allow-Credentials", "true");
        return response.setComplete();
    }

    @Override
    public int getOrder() {
        // 优先级设置，数字越小越先执行
        return -1;
    }
}