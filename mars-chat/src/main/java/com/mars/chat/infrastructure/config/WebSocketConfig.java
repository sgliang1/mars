package com.mars.chat.infrastructure.config;

import com.mars.chat.infrastructure.websocket.ChatEndpoint;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

@Configuration
public class WebSocketConfig {

    public WebSocketConfig(ApplicationContext context) {
        // å°?context æ³¨å…¥ç»?ChatEndpoint
        ChatEndpoint.setApplicationContext(context);
    }

    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }
}
