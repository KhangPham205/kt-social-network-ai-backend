package com.kt.social.domain.friendship.controller;

import com.kt.social.common.constants.ApiConstants;
import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.friendship.dto.FriendshipResponse;
import com.kt.social.domain.friendship.service.FriendshipService;
import com.kt.social.domain.user.dto.UserRelationDto;
import com.kt.social.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(ApiConstants.FRIENDSHIP)
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

    // thay toàn bộ UserProfileDto -> UserRelationDto
    @GetMapping("/sent")
    public ResponseEntity<PageVO<UserRelationDto>> getSentRequests(
            @ParameterObject Pageable pageable,
            @RequestParam(value = "filter", required = false) String filter
    ) {
        Long currentUserId = userService.getCurrentUser().getId();
        return ResponseEntity.ok(friendshipService.getSentRequests(currentUserId, filter, pageable));
    }

    @GetMapping
    public ResponseEntity<PageVO<UserRelationDto>> getMyFriends(
            @ParameterObject Pageable pageable,
            @RequestParam(value = "filter", required = false) String filter
    ) {
        Long currentUserId = userService.getCurrentUser().getId();
        return ResponseEntity.ok(friendshipService.getFriends(currentUserId, filter, pageable));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<PageVO<UserRelationDto>> getFriends(
            @PathVariable Long userId,
            @ParameterObject Pageable pageable,
            @RequestParam(value = "filter", required = false) String filter
    ) {
        return ResponseEntity.ok(friendshipService.getFriends(userId, filter, pageable));
    }

    @GetMapping("/pending")
    public ResponseEntity<PageVO<UserRelationDto>> getPendingRequests(
            @ParameterObject Pageable pageable,
            @RequestParam(value = "filter", required = false) String filter
    ) {
        Long currentUserId = userService.getCurrentUser().getId();
        return ResponseEntity.ok(friendshipService.getPendingRequests(currentUserId, filter, pageable));
    }

    @GetMapping("/blocked")
    public ResponseEntity<PageVO<UserRelationDto>> getBlockedUsers(
            @ParameterObject Pageable pageable,
            @RequestParam(value = "filter", required = false) String filter
    ) {
        Long currentUserId = userService.getCurrentUser().getId();
        return ResponseEntity.ok(friendshipService.getBlockedUsers(currentUserId, filter, pageable));
    }
}