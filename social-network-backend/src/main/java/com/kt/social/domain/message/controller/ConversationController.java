// ConversationController.java (get my conversations)
package com.kt.social.domain.message.controller;

import com.kt.social.domain.message.dto.*;
import com.kt.social.domain.message.service.ConversationService;
import com.kt.social.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;
    private final UserService userService;

    @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ConversationResponse> create(@ModelAttribute ConversationCreateRequest req) {
        return ResponseEntity.ok(conversationService.createConversation(req));
    }

    @PutMapping(value = "/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ConversationSummaryResponse> updateConversation(
            @ModelAttribute UpdateConversationRequest request
    ) {
        Long currentUserId = userService.getCurrentUser().getId();
        return ResponseEntity.ok(
                conversationService.updateConversation(currentUserId, request)
        );
    }

    @PostMapping("/addMembers")
    public ResponseEntity<ConversationSummaryResponse> addMembers(
            @RequestBody AddMembersRequest request
    ) {
        Long currentUserId = userService.getCurrentUser().getId();
        return ResponseEntity.ok(
                conversationService.addMembersToGroup(currentUserId, request)
        );
    }

    @PutMapping("/updateRoleMember")
    public ResponseEntity<ConversationSummaryResponse> updateMemberRole(
            @RequestBody UpdateMemberRoleRequest request
    ) {
        Long currentUserId = userService.getCurrentUser().getId();
        return ResponseEntity.ok(
                conversationService.updateMemberRole(currentUserId, request)
        );
    }

    @DeleteMapping("/{conversationId}/members/{userIdToRemove}")
    public ResponseEntity<ConversationSummaryResponse> removeMember(
            @PathVariable Long conversationId,
            @PathVariable Long userIdToRemove
    ) {
        Long currentUserId = userService.getCurrentUser().getId();
        return ResponseEntity.ok(
                conversationService.removeMemberFromGroup(currentUserId, conversationId, userIdToRemove)
        );
    }

    @DeleteMapping("/{conversationId}/leave")
    public ResponseEntity<Void> leaveConversation(@PathVariable Long conversationId) {
        Long currentUserId = userService.getCurrentUser().getId();
        conversationService.leaveConversation(currentUserId, conversationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<List<ConversationSummaryResponse>> myConversations(Principal principal) {
        Long userId = userService.getCurrentUser().getId();
        return ResponseEntity.ok(conversationService.getUserConversations(userId));
    }

    @GetMapping("/{conversationId}")
    public ResponseEntity<ConversationSummaryResponse> getConversationById(
            @PathVariable Long conversationId
    ) {
        Long currentUserId = userService.getCurrentUser().getId();
        return ResponseEntity.ok(
                conversationService.getConversationById(currentUserId, conversationId)
        );
    }
}