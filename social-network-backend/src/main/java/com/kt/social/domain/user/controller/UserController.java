package com.kt.social.domain.user.controller;

import com.kt.social.domain.user.dto.*;
import com.kt.social.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/{id}")
    public ResponseEntity<UserProfileDto> getUserProfile(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getProfile(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserProfileDto> updateProfile(@PathVariable Long id, @RequestBody UpdateUserProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(id, request));
    }

    @PostMapping("/{id}/follow")
    public ResponseEntity<FollowResponse> follow(@PathVariable Long id, @RequestParam Long targetId) {
        return ResponseEntity.ok(userService.followUser(id, targetId));
    }

    @DeleteMapping("/{id}/unfollow")
    public ResponseEntity<FollowResponse> unfollow(@PathVariable Long id, @RequestParam Long targetId) {
        return ResponseEntity.ok(userService.unfollowUser(id, targetId));
    }

    @GetMapping("/{id}/followers")
    public ResponseEntity<List<UserProfileDto>> getFollowers(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getFollowers(id));
    }

    @GetMapping("/{id}/following")
    public ResponseEntity<List<UserProfileDto>> getFollowing(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getFollowing(id));
    }
}