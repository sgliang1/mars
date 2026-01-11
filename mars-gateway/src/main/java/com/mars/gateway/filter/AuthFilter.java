package com.mars.gateway.filter;

import com.mars.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
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

/**
 * 网关全局鉴权过滤器
 * 修改说明：
 * 1. 移除了重复的硬编码密钥逻辑
 * 2. 统一调用 JwtUtil.parseToken 进行验签
 * 3. 增加了中文用户名的 URLEncode 处理，防止乱码
 */
@Component
public class AuthFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 1. 白名单放行：登录和注册接口不需要 Token
        if (path.contains("/mars-auth/login") || path.contains("/mars-auth/register")) {
            return chain.filter(exchange);
        }

        // 2. 检查 Authorization Header 是否存在且格式正确
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unAuthorized(exchange);
        }

        // 3. 截取 Token 并验签
        String token = authHeader.substring(7); // 去掉 "Bearer "
        try {
            // 直接调用 JwtUtil 解析，逻辑统一
            Claims claims = JwtUtil.parseToken(token);

            String userId = claims.get("userId").toString();
            String username = claims.get("username").toString();

            // 对用户名进行 URL 编码，防止 Header 中传递中文导致乱码或报错
            String encodedUsername = URLEncoder.encode(username, StandardCharsets.UTF_8);

            // 将用户信息放入 Header 传递给下游微服务
            ServerHttpRequest mutatedRequest = request.mutate()
                    .header("X-User-Id", userId)
                    .header("X-User-Name", encodedUsername)
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (Exception e) {
            // Token 解析失败（过期、篡改、密钥不匹配等）
            return unAuthorized(exchange);
        }
    }

    /**
     * 返回 401 未授权响应
     */
    private Mono<Void> unAuthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return response.setComplete();
    }

    @Override
    public int getOrder() {
        // 优先级设置，数字越小越先执行
        return -1;
    }
}