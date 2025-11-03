package com.kt.social.domain.user.service;

import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.user.dto.*;
import com.kt.social.domain.user.model.User;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

public interface UserService {
    User getCurrentUser();
    UserProfileDto getProfile(Long userId);
    UserProfileDto updateProfile(UpdateUserProfileRequest request);
    FollowResponse followUser(Long followerId, Long followingId);
    FollowResponse unfollowUser(Long followerId, Long followingId);
    PageVO<UserProfileDto> searchUsers(String filter, Pageable pageable);
    PageVO<UserRelationDto> getFollowersPaged(Long userId, Pageable pageable);
    PageVO<UserRelationDto> getFollowingPaged(Long userId, Pageable pageable);
    UserProfileDto getProfileByUsername(String username);
    UserProfileDto updateAvatar(MultipartFile avatarFile);
    UserRelationDto getRelationWithUser(Long targetUserId);
}
