// MessageController.java
package com.kt.social.domain.message.controller;

import com.kt.social.common.constants.ApiConstants;
import com.kt.social.domain.message.dto.MessageRequest;
import com.kt.social.domain.message.dto.MessageResponse;
import com.kt.social.domain.message.service.MessageService;
import com.kt.social.common.vo.CursorPage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(ApiConstants.MESSAGES)
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    // POST multipart to send (returns created message object)
    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<Map<String,Object>> sendMessage(@ModelAttribute MessageRequest req) {
        Map<String,Object> saved = messageService.sendMessage(req);
        return ResponseEntity.ok(saved);
    }

    // Cursor paging: newest page: GET /api/v1/messages/{conversationId}?limit=30
    // older page: add ?before=<messageId>
    @GetMapping("/{conversationId}/cursor")
    public ResponseEntity<CursorPage<MessageResponse>> getMessagesCursor(
            @PathVariable Long conversationId,
            @RequestParam(required = false) String before,
            @RequestParam(defaultValue = "30") int limit
    ) {
        CursorPage<MessageResponse> page = messageService.getMessagesCursor(conversationId, before, limit);
        return ResponseEntity.ok(page);
    }

    // convenience endpoint to fetch all messages (careful)
    @GetMapping("/{conversationId}/all")
    public ResponseEntity<List<Map<String,Object>>> getAllMessages(@PathVariable Long conversationId) {
        return ResponseEntity.ok(messageService.getMessages(conversationId));
    }
}