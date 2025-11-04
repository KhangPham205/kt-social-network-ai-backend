package com.kt.social.infra.websocket;

import com.kt.social.auth.model.UserCredential;
import com.kt.social.auth.repository.UserCredentialRepository;
import com.kt.social.auth.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
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
            String query = uri.getQuery(); // ví dụ: token=abc.def.ghi

            if (query == null || !query.startsWith("token=")) {
                System.out.println("❌ Missing token param");
                response.setStatusCode(HttpStatus.FORBIDDEN);
                return false;
            }

            String token = query.substring("token=".length());

            if (!jwtProvider.validateToken(token)) {
                System.out.println("❌ Invalid token format");
                response.setStatusCode(HttpStatus.FORBIDDEN);
                return false;
            }

            String username = jwtProvider.extractUsername(token);
            Long userId = jwtProvider.extractUserId(token);

            UserCredential userCred = credentialRepo.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found: " + username));

            // 🔥 Lưu đầy đủ thông tin user vào attributes
            attributes.put("user", userCred);
            attributes.put("userId", userId);
            attributes.put("username", username);

            System.out.printf("✅ WebSocket authenticated userId=%d username=%s%n", userId, username);
            return true;

        } catch (Exception e) {
            System.out.println("❌ JWT handshake failed: " + e.getMessage());
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // no-op
    }
}