package com.kt.social.domain.friendship.controller;

import com.kt.social.domain.friendship.dto.FriendshipResponse;
import com.kt.social.domain.friendship.service.FriendshipService;
import com.kt.social.domain.user.dto.UserProfileDto;
import com.kt.social.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/friends")
@RequiredArgsConstructor
public class FriendshipController {

    private final UserService userService;
    private final FriendshipService friendshipService;

    // Gửi lời mời kết bạn
    @PostMapping("/send")
    public ResponseEntity<FriendshipResponse> sendRequest(@RequestParam Long targetId) {
        Long currentUserId = userService.getCurrentUser().getId();
        return ResponseEntity.ok(friendshipService.sendRequest(currentUserId, targetId));
    }

    // Hủy yêu cầu lời mời kết bạn
    @PostMapping("/unsend")
    public ResponseEntity<FriendshipResponse> unsendRequest(@RequestParam Long targetId) {
        Long currentUserId = userService.getCurrentUser().getId();
        return ResponseEntity.ok(friendshipService.unsendRequest(currentUserId, targetId));
    }

    // Chấp nhận lời mời
    @PostMapping("/accept")
    public ResponseEntity<FriendshipResponse> acceptRequest(@RequestParam Long requesterId) {
        Long currentUserId = userService.getCurrentUser().getId();
        return ResponseEntity.ok(friendshipService.acceptRequest(requesterId, currentUserId));
    }

    // Từ chối lời mời
    @PostMapping("/reject")
    public ResponseEntity<FriendshipResponse> rejectRequest(@RequestParam Long requesterId) {
        Long currentUserId = userService.getCurrentUser().getId();
        return ResponseEntity.ok(friendshipService.rejectRequest(requesterId, currentUserId));
    }

    // Hủy kết bạn
    @DeleteMapping("/unfriend")
    public ResponseEntity<FriendshipResponse> unfriend(@RequestParam Long friendId) {
        Long currentUserId = userService.getCurrentUser().getId();
        return ResponseEntity.ok(friendshipService.unfriend(currentUserId, friendId));
    }

    // Chặn người khác
    @PostMapping("/block")
    public ResponseEntity<FriendshipResponse> blockUser(@RequestParam Long targetId) {
        Long currentUserId = userService.getCurrentUser().getId();
        return ResponseEntity.ok(friendshipService.blockUser(currentUserId, targetId));
    }

    // Bỏ chặn
    @DeleteMapping("/unblock")
    public ResponseEntity<FriendshipResponse> unblockUser(@RequestParam Long targetId) {
        Long currentUserId = userService.getCurrentUser().getId();
        return ResponseEntity.ok(friendshipService.unblockUser(currentUserId, targetId));
    }

    @GetMapping("/sent")
    public ResponseEntity<List<UserProfileDto>> getSentRequests() {
        Long currentUserId = userService.getCurrentUser().getId();
        return ResponseEntity.ok(friendshipService.getSentRequests(currentUserId));
    }

    // Lấy danh sách bạn bè của chính mình
    @GetMapping
    public ResponseEntity<List<UserProfileDto>> getMyFriends() {
        Long currentUserId = userService.getCurrentUser().getId();
        return ResponseEntity.ok(friendshipService.getFriends(currentUserId));
    }

    // Lấy danh sách bạn bè của user khác
    @GetMapping("/{userId}")
    public ResponseEntity<List<UserProfileDto>> getFriends(@PathVariable Long userId) {
        return ResponseEntity.ok(friendshipService.getFriends(userId));
    }

    // Lấy danh sách lời mời kết bạn đang chờ
    @GetMapping("/pending")
    public ResponseEntity<List<UserProfileDto>> getPendingRequests() {
        Long currentUserId = userService.getCurrentUser().getId();
        return ResponseEntity.ok(friendshipService.getPendingRequests(currentUserId));
    }

    // Lấy danh sách người bị chặn
    @GetMapping("/blocked")
    public ResponseEntity<List<UserProfileDto>> getBlockedUsers() {
        Long currentUserId = userService.getCurrentUser().getId();
        return ResponseEntity.ok(friendshipService.getBlockedUsers(currentUserId));
    }
}