package com.kt.social.domain.friendship.controller;

import com.kt.social.auth.model.UserCredential;
import com.kt.social.auth.repository.UserCredentialRepository;
import com.kt.social.auth.util.SecurityUtils;
import com.kt.social.domain.friendship.dto.FriendshipResponse;
import com.kt.social.domain.friendship.service.FriendshipService;
import com.kt.social.domain.user.dto.UserProfileDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/friends")
@RequiredArgsConstructor
public class FriendshipController {

    private final FriendshipService friendshipService;
    private final UserCredentialRepository credentialRepository;

    @PostMapping("/send")
    public ResponseEntity<FriendshipResponse> sendRequest(@RequestParam Long targetId) {
        Long currentUserId = SecurityUtils.getCurrentUserCredential(credentialRepository)
                .map(UserCredential::getId)
                .orElseThrow(() -> new RuntimeException("User not authenticated"));
        return ResponseEntity.ok(friendshipService.sendRequest(currentUserId, targetId));
    }

    @PostMapping("/accept")
    public ResponseEntity<FriendshipResponse> acceptRequest(@RequestParam Long requesterId) {
        Long currentUserId = SecurityUtils.getCurrentUserCredential(credentialRepository)
                .map(UserCredential::getId)
                .orElseThrow(() -> new RuntimeException("User not authenticated"));
        return ResponseEntity.ok(friendshipService.acceptRequest(currentUserId, requesterId));
    }

    @PostMapping("/reject")
    public ResponseEntity<FriendshipResponse> rejectRequest(@RequestParam Long requesterId) {
        Long currentUserId = SecurityUtils.getCurrentUserCredential(credentialRepository)
                .map(UserCredential::getId)
                .orElseThrow(() -> new RuntimeException("User not authenticated"));
        return ResponseEntity.ok(friendshipService.rejectRequest(currentUserId, requesterId));
    }

    @DeleteMapping("/unfriend")
    public ResponseEntity<FriendshipResponse> unfriend(@RequestParam Long friendId) {
        Long currentUserId = SecurityUtils.getCurrentUserCredential(credentialRepository)
                .map(UserCredential::getId)
                .orElseThrow(() -> new RuntimeException("User not authenticated"));
        return ResponseEntity.ok(friendshipService.unfriend(currentUserId, friendId));
    }

    @GetMapping
    public ResponseEntity<List<UserProfileDto>> getMyFriends() {
        Long currentUserId = SecurityUtils.getCurrentUserCredential(credentialRepository)
                .map(UserCredential::getId)
                .orElseThrow(() -> new RuntimeException("User not authenticated"));
        return ResponseEntity.ok(friendshipService.getFriends(currentUserId));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<List<UserProfileDto>> getFriends(@PathVariable Long userId) {
        return ResponseEntity.ok(friendshipService.getFriends(userId));
    }
}