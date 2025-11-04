package com.kt.social.infra.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kt.social.domain.message.dto.MessageRequest;
import com.kt.social.domain.message.dto.MessageResponse;
import com.kt.social.domain.message.service.MessageService;
import com.kt.social.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class MessageWebSocketHandler extends TextWebSocketHandler {

    private final MessageService messageService;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    // map userId → WebSocketSession
    private final Map<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Object userObj = session.getAttributes().get("user");
        if (userObj == null) {
            System.out.println("❌ Missing user attribute in session");
            try {
                session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Missing authentication"));
            } catch (Exception ignored) {}
            return;
        }

        // Lưu session theo userId
        com.kt.social.auth.model.UserCredential user = (com.kt.social.auth.model.UserCredential) userObj;
        Long userId = user.getId();
        sessions.put(userId, session);
        System.out.println("✅ Connected userId=" + userId + ", username=" + user.getUsername());

        // Cập nhật trạng thái online
        userRepository.findById(userId).ifPresent(u -> {
            u.setIsOnline(true);
            userRepository.save(u);
        });
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            String username = (String) session.getAttributes().get("username");
            Long userId = (Long) session.getAttributes().get("userId");

            MessageRequest request = objectMapper.readValue(message.getPayload(), MessageRequest.class);

            // Lưu DB
            MessageResponse saved = messageService.sendMessageAs(userId, request);
            System.out.println("💬 " + saved.getSenderName() + " -> conversation "
                    + saved.getConversationId() + ": " + saved.getContent());

            // Gửi tới tất cả user trong conversation
            for (Long memberId : messageService.getConversationMembers(saved.getConversationId())) {
                WebSocketSession target = sessions.get(memberId);
                if (target != null && target.isOpen()) {
                    target.sendMessage(new TextMessage(objectMapper.writeValueAsString(saved)));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            try {
                session.sendMessage(new TextMessage("{\"error\": \"" + e.getMessage() + "\"}"));
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = sessions.entrySet().stream()
                .filter(e -> e.getValue().equals(session))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);

        if (userId != null) {
            sessions.remove(userId);
            System.out.println("❌ Disconnected userId=" + userId);

            // Cập nhật offline
            userRepository.findById(userId).ifPresent(u -> {
                u.setIsOnline(false);
                userRepository.save(u);
            });
        }
    }
}