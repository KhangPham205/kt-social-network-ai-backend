package com.kt.social.domain.friendship.controller;

import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.friendship.dto.FriendshipResponse;
import com.kt.social.domain.friendship.service.FriendshipService;
import com.kt.social.domain.user.dto.UserProfileDto;
import com.kt.social.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.parameters.P;
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
    public ResponseEntity<PageVO<UserProfileDto>> getSentRequests(@ParameterObject Pageable pageable) {
        Long currentUserId = userService.getCurrentUser().getId();
        return ResponseEntity.ok(friendshipService.getSentRequests(currentUserId, pageable));
    }

    // Lấy danh sách bạn bè của chính mình
    @GetMapping
    public ResponseEntity<PageVO<UserProfileDto>> getMyFriends(@ParameterObject Pageable pageable) {
        Long currentUserId = userService.getCurrentUser().getId();
        return ResponseEntity.ok(friendshipService.getFriends(currentUserId, pageable));
    }

    // Lấy danh sách bạn bè của user khác
    @GetMapping("/{userId}")
    public ResponseEntity<PageVO<UserProfileDto>> getFriends(@PathVariable Long userId, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(friendshipService.getFriends(userId, pageable));
    }

    // Lấy danh sách lời mời kết bạn đang chờ
    @GetMapping("/pending")
    public ResponseEntity<PageVO<UserProfileDto>> getPendingRequests(@ParameterObject Pageable pageable) {
        Long currentUserId = userService.getCurrentUser().getId();
        return ResponseEntity.ok(friendshipService.getPendingRequests(currentUserId, pageable));
    }

    // Lấy danh sách người bị chặn
    @GetMapping("/blocked")
    public ResponseEntity<PageVO<UserProfileDto>> getBlockedUsers(@ParameterObject Pageable pageable) {
        Long currentUserId = userService.getCurrentUser().getId();
        return ResponseEntity.ok(friendshipService.getBlockedUsers(currentUserId, pageable));
    }
}