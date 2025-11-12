package com.kt.social.domain.message.controller;

import com.kt.social.auth.repository.UserCredentialRepository;
import com.kt.social.auth.util.SecurityUtils;
import com.kt.social.common.exception.BadRequestException;
import com.kt.social.domain.message.dto.MessageRequest;
import com.kt.social.domain.message.service.MessageService;
import com.kt.social.domain.user.repository.UserRepository;
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
public class ChatController { // <-- Tên file của bạn

    private final MessageService messageService;
    private final UserService userService;

    @MessageMapping("/chat.send")
    public void handleChatMessage(@Payload MessageRequest messageRequest, Principal principal) {

        if (principal == null || principal.getName() == null) {
            throw new BadRequestException("Unauthenticated user tried to send a message.");
        }

        String senderId = (String) ((UsernamePasswordAuthenticationToken) principal).getPrincipal();

        messageService.sendMessageAs(Long.parseLong(senderId), messageRequest);
    }

    @MessageMapping("/chat.addUser")
    @SendTo("/topic/public")
    public void addUser(
            @Payload MessageRequest messageRequest,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        // ...
    }
}