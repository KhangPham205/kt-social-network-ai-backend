package com.kt.social.infra.websocket;

import com.kt.social.auth.security.JwtProvider;
import com.kt.social.auth.repository.UserCredentialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.security.Principal;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtProvider jwtProvider;
    private final UserCredentialRepository credentialRepo;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {

        try {
            URI uri = request.getURI();
            String query = uri.getQuery(); // token=xxxx
            if (query == null || !query.startsWith("token=")) {
                System.out.println("❌ Missing token param");
                response.setStatusCode(org.springframework.http.HttpStatus.FORBIDDEN);
                return false;
            }

            String token = query.substring("token=".length());
            String username = jwtProvider.extractUsername(token);
            var userCred = credentialRepo.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // map vào Principal cho WebSocketSession
            attributes.put("user", userCred);
            attributes.put("username", username);

            System.out.println("✅ WebSocket authenticated: " + username);
            return true;
        } catch (Exception e) {
            System.out.println("❌ Invalid JWT: " + e.getMessage());
            response.setStatusCode(org.springframework.http.HttpStatus.FORBIDDEN);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // không cần xử lý
    }
}