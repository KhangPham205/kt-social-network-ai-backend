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
    FollowResponse followUser(Long followingId);
    FollowResponse unfollowUser(Long followingId);
    FollowResponse removeFollower(Long followerId);
    PageVO<UserRelationDto> searchUsers(String filter, Pageable pageable);
    PageVO<UserRelationDto> getFollowersPaged(Long userId, Pageable pageable);
    PageVO<UserRelationDto> getFollowingPaged(Long userId, Pageable pageable);
    UserProfileDto updateAvatar(MultipartFile avatarFile);
    UserRelationDto getRelationWithUser(Long targetUserId);
}
