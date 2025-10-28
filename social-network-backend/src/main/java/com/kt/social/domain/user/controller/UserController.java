package com.kt.social.domain.user.controller;

import com.kt.social.auth.model.UserCredential;
import com.kt.social.auth.repository.UserCredentialRepository;
import com.kt.social.auth.util.SecurityUtils;
import com.kt.social.domain.user.dto.*;
import com.kt.social.domain.user.model.User;
import com.kt.social.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
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

    @GetMapping("/me")
    public ResponseEntity<UserProfileDto> getMyProfile(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(userService.getProfileByUsername(userDetails.getUsername()));
    }

    @PutMapping("/me")
    public ResponseEntity<UserProfileDto> updateProfile(@RequestBody UpdateUserProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(request));
    }

    @PostMapping("/follow")
    public ResponseEntity<FollowResponse> follow(@RequestParam Long targetId) {
        User currentUser = userService.getCurrentUser();
        return ResponseEntity.ok(userService.followUser(currentUser.getId(), targetId));
    }

    @DeleteMapping("/unfollow")
    public ResponseEntity<FollowResponse> unfollow(@RequestParam Long targetId) {
        User currentUser = userService.getCurrentUser();
        return ResponseEntity.ok(userService.unfollowUser(currentUser.getId(), targetId));
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