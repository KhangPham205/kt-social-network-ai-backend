package com.kt.social.domain.user.service;

import com.kt.social.domain.user.model.User;
import com.kt.social.domain.user.dto.*;

import java.util.List;

public interface UserService {
    UserProfileDto getProfile(Long userId);
    UserProfileDto updateProfile(Long userId, UpdateUserProfileRequest request);
    FollowResponse followUser(Long followerId, Long followingId);
    FollowResponse unfollowUser(Long followerId, Long followingId);
    FollowResponse followUserByUsername(String username, Long targetId);
    List<UserProfileDto> getFollowers(Long userId);
    List<UserProfileDto> getFollowing(Long userId);

    UserProfileDto getProfileByUsername(String username);
}
