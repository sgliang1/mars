package com.mars.chat.config;

import com.mars.chat.ws.ChatEndpoint;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

@Configuration
public class WebSocketConfig {

    public WebSocketConfig(ApplicationContext context) {
        // 将 context 注入给 ChatEndpoint
        ChatEndpoint.setApplicationContext(context);
    }

    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }
}