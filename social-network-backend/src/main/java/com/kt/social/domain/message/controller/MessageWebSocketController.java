package com.kt.social.domain.message.controller;

import com.kt.social.domain.message.dto.MessageRequest;
import com.kt.social.domain.message.dto.MessageResponse;
import com.kt.social.domain.message.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class MessageWebSocketController {

    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload MessageRequest request) {
        MessageResponse saved = messageService.sendMessage(request);

        // gửi message đến tất cả user trong conversation
        messageService.getConversationMembers(saved.getConversationId())
                .forEach(userId ->
                        messagingTemplate.convertAndSendToUser(
                                String.valueOf(userId),
                                "/queue/messages",
                                saved
                        ));

        // publish event "delivered"
        messagingTemplate.convertAndSend(
                "/topic/conversation/" + saved.getConversationId() + "/delivered",
                saved
        );
    }

    @MessageMapping("/chat.readMessage")
    public void markAsRead(@Payload Long messageId, Principal principal) {
        messageService.markAsRead(messageId);

        // Phát event “read” cho conversation
        messagingTemplate.convertAndSend("/topic/message/" + messageId + "/read", principal.getName());
    }
}