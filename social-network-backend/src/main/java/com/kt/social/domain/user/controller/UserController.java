package com.kt.social.domain.user.controller;

import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.user.dto.*;
import com.kt.social.domain.user.model.User;
import com.kt.social.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @PutMapping(value = "/me", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserProfileDto> updateProfile(@ModelAttribute UpdateUserProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(request));
    }

    @PutMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserProfileDto> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(userService.updateAvatar(file));
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
    public ResponseEntity<PageVO<UserRelationDto>> getFollowers(
            @PathVariable Long id,
            @ParameterObject Pageable pageable
    ) {
        return ResponseEntity.ok(userService.getFollowersPaged(id, pageable));
    }

    @GetMapping("/{id}/following")
    public ResponseEntity<PageVO<UserRelationDto>> getFollowing(
            @PathVariable Long id,
            @ParameterObject Pageable pageable
    ) {
        return ResponseEntity.ok(userService.getFollowingPaged(id, pageable));
    }

    @GetMapping("/{id}/relation")
    public ResponseEntity<UserRelationDto> getRelationWith(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getRelationWithUser(id));
    }

    @GetMapping("/{id}/friendship")
    public ResponseEntity<FriendshipStatusDto> getFriendshipWith(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getFriendshipStatusWithUser(id));
    }
}