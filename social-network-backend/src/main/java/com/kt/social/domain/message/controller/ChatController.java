package com.kt.social.domain.message.controller;

import com.kt.social.common.constants.WebSocketConstants;
import com.kt.social.common.exception.BadRequestException;
import com.kt.social.domain.message.dto.MessageRequest;
import com.kt.social.domain.message.service.MessageService;
import com.kt.social.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatController {

    private final MessageService messageService;
    private final UserService userService;

    /**
     * 📌 Gửi tin nhắn text qua WebSocket
     * FE chỉ gửi content + conversationId + replyToId
     */
    @MessageMapping(WebSocketConstants.CHAT_SEND)
    public void handleChatMessage(
            @Payload MessageRequest messageRequest,
            Principal principal
    ) {

        if (principal == null) {
            throw new BadRequestException("Unauthenticated WebSocket request.");
        }

        String senderId = (String) ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
        Long uid = Long.parseLong(senderId);

        // WebSocket KHÔNG hỗ trợ multipart => mediaFiles phải null
        messageRequest.setMediaFiles(null);

        // 🚀 Gửi tin nhắn
        messageService.sendMessageAs(uid, messageRequest);

        log.info("User {} sent WS message in conversation {}", senderId, messageRequest.getConversationId());
    }

    /**
     * 📌 WS event khi user join (typing indicator / online presence)
     */
    @MessageMapping(WebSocketConstants.CHAT_ADD_USER)
    public String addUser(
            @Payload MessageRequest messageRequest,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        headerAccessor.getSessionAttributes().put("username", messageRequest.getContent());
        return messageRequest.getContent() + " joined the chat";
    }
}