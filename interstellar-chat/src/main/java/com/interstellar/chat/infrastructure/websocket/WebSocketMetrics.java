package com.interstellar.chat.infrastructure.websocket;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 自定义 Prometheus 业务指标
 * 暴露 WebSocket 在线用户数到 /actuator/prometheus
 */
@Component
public class WebSocketMetrics {

    @Autowired
    public WebSocketMetrics(MeterRegistry registry, WebSocketSessionManager sessionManager) {
        Gauge.builder("ws_online_users", sessionManager, WebSocketSessionManager::localOnlineCount)
                .description("当前 WebSocket 在线用户数")
                .register(registry);
    }
}