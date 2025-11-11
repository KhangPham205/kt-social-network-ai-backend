package com.kt.social.domain.message.controller;

import com.kt.social.domain.message.dto.MessageRequest;
import com.kt.social.domain.message.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> sendMessage(@ModelAttribute MessageRequest req) {
        return ResponseEntity.ok(messageService.sendMessage(req));
    }

    @GetMapping("/{conversationId}")
    public ResponseEntity<List<Map<String, Object>>> getMessages(@PathVariable Long conversationId) {
        return ResponseEntity.ok(messageService.getMessages(conversationId));
    }
}