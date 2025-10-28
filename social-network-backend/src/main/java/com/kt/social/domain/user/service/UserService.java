package com.kt.social.domain.user.service;

import com.kt.social.domain.user.dto.*;
import com.kt.social.domain.user.model.User;

import java.util.List;
import java.util.Optional;

public interface UserService {
    User getCurrentUser();
    UserProfileDto getProfile(Long userId);
    UserProfileDto updateProfile(UpdateUserProfileRequest request);
    FollowResponse followUser(Long followerId, Long followingId);
    FollowResponse unfollowUser(Long followerId, Long followingId);
    List<UserProfileDto> getFollowers(Long userId);
    List<UserProfileDto> getFollowing(Long userId);

    UserProfileDto getProfileByUsername(String username);
}
