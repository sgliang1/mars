package com.mars.gateway.filter;

import io.opentelemetry.api.trace.Span;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;

/**
 * 网关访问日志过滤器
 * 记录每个请求的 method、path、status、耗时、userId、traceId
 * 优先级低于 AuthFilter（Order=-1）和 RateLimitFilter（Order=1）
 * 确保日志在鉴权和限流之后记录
 */
@Slf4j
@Component
public class AccessLogFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        ServerHttpRequest request = exchange.getRequest();
        String method = request.getMethod().name();
        URI uri = request.getURI();
        String path = uri.getPath();
        String userId = request.getHeaders().getFirst("X-User-Id");
        String clientIp = request.getRemoteAddress() != null
                ? request.getRemoteAddress().getAddress().getHostAddress() : "unknown";

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            long duration = System.currentTimeMillis() - startTime;
            int statusCode = exchange.getResponse().getStatusCode() != null
                    ? exchange.getResponse().getStatusCode().value() : 0;

            String traceId = Span.current().getSpanContext().getTraceId();

            log.info("ACCESS | {} {} | status={} | duration={}ms | userId={} | ip={} | traceId={}",
                    method, path, statusCode, duration,
                    userId != null ? userId : "-", clientIp, traceId);

            // 慢请求告警
            if (duration > 3000) {
                log.warn("SLOW_REQUEST | {} {} | duration={}ms | userId={} | traceId={}",
                        method, path, duration, userId, traceId);
            }
        }));
    }

    @Override
    public int getOrder() {
        return 10; // 在 AuthFilter(-1) 和 RateLimitFilter(1) 之后执行
    }
}