package com.kt.social.domain.message.controller;

import com.kt.social.domain.message.dto.ConversationCreateRequest;
import com.kt.social.domain.message.dto.ConversationResponse;
import com.kt.social.domain.message.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    // ✅ Tạo nhóm chat hoặc chat riêng
    @PostMapping
    public ResponseEntity<ConversationResponse> createConversation(@RequestBody ConversationCreateRequest req) {
        return ResponseEntity.ok(conversationService.createConversation(req));
    }
}