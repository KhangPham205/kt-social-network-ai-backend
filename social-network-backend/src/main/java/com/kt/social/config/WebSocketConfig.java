package com.kt.social.config;

import com.kt.social.domain.message.service.MessageService;
import com.kt.social.infra.websocket.JwtHandshakeInterceptor;
import com.kt.social.infra.websocket.MessageWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;
    private final MessageWebSocketHandler messageWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(messageWebSocketHandler, "/ws", "/ws/chat", "/ws/notification")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}