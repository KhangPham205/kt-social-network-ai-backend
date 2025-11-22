package com.kt.social.infra.websocket;

import com.kt.social.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.time.Instant;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;

    // Lưu danh sách user online trong bộ nhớ (Map<UserId, Count>)
    // Nếu scale nhiều server thì phải dùng Redis
    // private final Set<String> onlineUsers = ConcurrentHashMap.newKeySet();

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = headerAccessor.getUser();

        if (user != null) {
            String userId = user.getName();
            log.info("✅ User Connected: " + userId);

            Map<String, Object> statusUpdate = Map.of(
                    "type", "USER_ONLINE",
                    "userId", userId,
                    "timestamp", System.currentTimeMillis()
            );
            messagingTemplate.convertAndSend("/topic/public", statusUpdate);
        }
    }

    @EventListener
    @Transactional
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal userPrincipal = headerAccessor.getUser();

        if (userPrincipal != null) {
            String userIdStr = userPrincipal.getName();
            Long userId = Long.parseLong(userIdStr);
            log.info("❌ User Disconnected: " + userId);

            Map<String, Object> statusUpdate = Map.of(
                    "type", "USER_OFFLINE",
                    "userId", userId,
                    "timestamp", Instant.now().toString()
            );
            messagingTemplate.convertAndSend("/topic/public", statusUpdate);

            userRepository.findById(userId).ifPresent(user -> {
                user.setLastActiveAt(Instant.now());
                userRepository.save(user);
            });
        }
    }
}