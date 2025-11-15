// ConversationController.java (get my conversations)
package com.kt.social.domain.message.controller;

import com.kt.social.domain.message.dto.ConversationCreateRequest;
import com.kt.social.domain.message.dto.ConversationResponse;
import com.kt.social.domain.message.service.ConversationService;
import com.kt.social.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<ConversationResponse> create(@RequestBody ConversationCreateRequest req) {
        return ResponseEntity.ok(conversationService.createConversation(req));
    }

    @GetMapping("/me")
    public ResponseEntity<List<Map<String,Object>>> myConversations(Principal principal) {
        Long userId = userService.getCurrentUser().getId();
        return ResponseEntity.ok(conversationService.getUserConversations(userId));
    }
}