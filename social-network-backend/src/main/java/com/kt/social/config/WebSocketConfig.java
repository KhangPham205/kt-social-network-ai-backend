package com.kt.social.config;

import com.kt.social.infra.websocket.JwtHandshakeInterceptor;
import com.kt.social.infra.websocket.StompPrincipalInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;
    private final StompPrincipalInterceptor stompPrincipalInterceptor;

    public WebSocketConfig(JwtHandshakeInterceptor jwtHandshakeInterceptor, StompPrincipalInterceptor stompPrincipalInterceptor) {
        this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
        this.stompPrincipalInterceptor = stompPrincipalInterceptor;
    }


    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompPrincipalInterceptor);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*") // ⚠️ Cực kỳ quan trọng để websocketking.com connect được
                .addInterceptors(jwtHandshakeInterceptor)
                .withSockJS(); // có thể thêm hoặc bỏ tùy client
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // client gửi tin → /app/...
        registry.setApplicationDestinationPrefixes("/app");

        // server broadcast tin → /topic/... hoặc /queue/...
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setUserDestinationPrefix("/user"); // cho chat 1-1
    }
}