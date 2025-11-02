package com.kt.social.domain.user.service.impl;

import com.kt.social.auth.model.UserCredential;
import com.kt.social.auth.repository.UserCredentialRepository;
import com.kt.social.auth.util.SecurityUtils;
import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.friendship.enums.FriendshipStatus;
import com.kt.social.domain.friendship.repository.FriendshipRepository;
import com.kt.social.domain.user.dto.*;
import com.kt.social.domain.user.mapper.UserMapper;
import com.kt.social.domain.user.model.*;
import com.kt.social.domain.user.repository.*;
import com.kt.social.domain.user.service.UserService;
import com.kt.social.infra.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserCredentialRepository userCredentialRepository;
    private final UserRepository userRepository;
    private final UserRelaRepository userRelaRepository;
    private final UserMapper userMapper;
    private final FriendshipRepository friendshipRepository;
    private final StorageService storageService;

    @Override
    public User getCurrentUser() {
        String username = SecurityUtils.getCurrentUsername()
                .orElseThrow(() -> new RuntimeException("User not authenticated"));

        return userRepository.findByCredentialUsername(username);
    }

    @Override
    public UserProfileDto getProfile(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return userMapper.toDto(user);
    }

    @Override
    @Transactional
    public UserProfileDto updateProfile(UpdateUserProfileRequest request) {
        var credential = SecurityUtils.getCurrentUserCredential(userCredentialRepository)
                .orElseThrow(() -> new RuntimeException("User not authenticated"));

        User user = userRepository.findById(credential.getUser().getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // cập nhật thông tin text
        if (request.getDisplayName() != null) user.setDisplayName(request.getDisplayName());
        if (request.getBio() != null && user.getUserInfo() != null)
            user.getUserInfo().setBio(request.getBio());
        if (request.getFavorites() != null && user.getUserInfo() != null)
            user.getUserInfo().setFavorites(request.getFavorites());
        if (request.getDateOfBirth() != null && user.getUserInfo() != null)
            user.getUserInfo().setDateOfBirth(request.getDateOfBirth());

        // nếu có file avatar
        if (request.getAvatarFile() != null && !request.getAvatarFile().isEmpty()) {
            String avatarUrl = storageService.saveFile(request.getAvatarFile(), "avatars");

            // xóa avatar cũ nếu có
            if (user.getAvatarUrl() != null && !user.getAvatarUrl().isBlank()) {
                storageService.deleteFile(user.getAvatarUrl());
            }

            user.setAvatarUrl(avatarUrl);
        }

        userRepository.save(user);
        return userMapper.toDto(user);
    }

    @Override
    public FollowResponse followUser(Long userId, Long targetId) {
        if (userId.equals(targetId))
            throw new RuntimeException("You cannot follow yourself");

        User follower = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        User following = userRepository.findById(targetId)
                .orElseThrow(() -> new RuntimeException("Target user not found"));

        boolean exists = userRelaRepository.existsByFollowerAndFollowing(follower, following);
        if (exists) {
            throw new RuntimeException("Already following");
        }

        UserRela rela = UserRela.builder()
                .follower(follower)
                .following(following)
                .build();

        userRelaRepository.save(rela);
        return new FollowResponse("Followed successfully", true);
    }

    @Override
    @Transactional
    public FollowResponse unfollowUser(Long userId, Long targetId) {
        if (userId.equals(targetId)) {
            throw new RuntimeException("You cannot unfollow yourself");
        }

        User follower = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        User following = userRepository.findById(targetId)
                .orElseThrow(() -> new RuntimeException("Target user not found"));

        boolean exists = userRelaRepository.existsByFollowerAndFollowing(follower, following);
        if (!exists) {
            throw new RuntimeException("You are not following this user");
        }

        userRelaRepository.deleteByFollowerAndFollowing(follower, following);
        return new FollowResponse("Unfollowed successfully", false);
    }

    @Override
    public List<UserProfileDto> getFollowers(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return userRelaRepository.findByFollowing(user)
                .stream()
                .map(UserRela::getFollower)
                .map(userMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<UserProfileDto> getFollowing(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return userRelaRepository.findByFollower(user)
                .stream()
                .map(UserRela::getFollowing)
                .map(userMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public PageVO<UserRelationDto> getFollowersPaged(Long userId, Pageable pageable) {
        User viewer = getCurrentUser();
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Page<UserRela> followers = userRelaRepository.findByFollowing(target, pageable);
        List<UserRelationDto> content = followers.stream()
                .map(UserRela::getFollower)
                .map(user -> mapToRelationDto(viewer, user))
                .toList();

        return PageVO.<UserRelationDto>builder()
                .page(followers.getNumber())
                .size(followers.getSize())
                .totalElements(followers.getTotalElements())
                .totalPages(followers.getTotalPages())
                .numberOfElements(content.size())
                .content(content)
                .build();
    }

    // ========== Following (phân trang + kèm quan hệ) ==========
    @Override
    @Transactional
    public PageVO<UserRelationDto> getFollowingPaged(Long userId, Pageable pageable) {
        User viewer = getCurrentUser();
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Page<UserRela> following = userRelaRepository.findByFollower(target, pageable);
        List<UserRelationDto> content = following.stream()
                .map(UserRela::getFollowing)
                .map(user -> mapToRelationDto(viewer, user))
                .toList();

        return PageVO.<UserRelationDto>builder()
                .page(following.getNumber())
                .size(following.getSize())
                .totalElements(following.getTotalElements())
                .totalPages(following.getTotalPages())
                .numberOfElements(content.size())
                .content(content)
                .build();
    }

    @Override
    public UserProfileDto getProfileByUsername(String username) {
        UserCredential cred = userCredentialRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Credential not found"));
        User user = userRepository.findByCredential(cred);
        return userMapper.toDto(user);
    }

    @Transactional
    @Override
    public UserProfileDto updateAvatar(MultipartFile avatarFile) {
        var credential = SecurityUtils.getCurrentUserCredential(userCredentialRepository)
                .orElseThrow(() -> new RuntimeException("User not authenticated"));

        User user = userRepository.findById(credential.getUser().getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Xóa ảnh cũ (nếu có)
        if (user.getAvatarUrl() != null) {
            storageService.deleteFile(user.getAvatarUrl());
        }

        // Lưu ảnh mới
        String avatarUrl = storageService.saveAvatar(avatarFile);
        user.setAvatarUrl(avatarUrl);
        userRepository.save(user);

        return userMapper.toDto(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserRelationDto getRelationWithUser(Long targetUserId) {
        User viewer = getCurrentUser();
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new RuntimeException("Target user not found"));

        boolean isFollowing = userRelaRepository.existsByFollowerAndFollowing(viewer, target);
        boolean isFollowedBy = userRelaRepository.existsByFollowerAndFollowing(target, viewer);

        boolean isFriend = friendshipRepository.existsByUserAndFriendAndStatus(viewer, target, FriendshipStatus.ACCEPTED)
                || friendshipRepository.existsByUserAndFriendAndStatus(target, viewer, FriendshipStatus.ACCEPTED);

        return UserRelationDto.builder()
                .id(target.getId())
                .displayName(target.getDisplayName())
                .avatarUrl(target.getAvatarUrl())
                .isActive(target.getIsActive())
                .bio(target.getUserInfo() != null ? target.getUserInfo().getBio() : null)
                .favorites(target.getUserInfo() != null ? target.getUserInfo().getFavorites() : null)
                .dateOfBirth(target.getUserInfo() != null ? target.getUserInfo().getDateOfBirth() : null)
                .isFollowing(isFollowing)
                .isFollowedBy(isFollowedBy)
                .isFriend(isFriend)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public FriendshipStatusDto getFriendshipStatusWithUser(Long targetUserId) {
        User viewer = getCurrentUser();
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new RuntimeException("Target user not found"));

        var friendshipOpt = friendshipRepository.findByUserAndFriend(viewer, target)
                .or(() -> friendshipRepository.findByUserAndFriend(target, viewer));

        if (friendshipOpt.isEmpty()) {
            return FriendshipStatusDto.builder()
                    .exists(false)
                    .status(null)
                    .build();
        }

        var friendship = friendshipOpt.get();

        return FriendshipStatusDto.builder()
                .exists(true)
                .status(friendship.getStatus())
                .requesterId(friendship.getUser().getId())
                .receiverId(friendship.getFriend().getId())
                .build();
    }

    // ========== Hàm tiện ích chuyển đổi User → UserRelationDto ==========
    private UserRelationDto mapToRelationDto(User viewer, User target) {
        boolean isFollowing = userRelaRepository.existsByFollowerAndFollowing(viewer, target);
        boolean isFollowedBy = userRelaRepository.existsByFollowerAndFollowing(target, viewer);
        boolean isFriend =
                friendshipRepository.existsByUserAndFriendAndStatus(viewer, target, FriendshipStatus.ACCEPTED)
                        || friendshipRepository.existsByUserAndFriendAndStatus(target, viewer, FriendshipStatus.ACCEPTED);

        // Lấy thông tin user đầy đủ từ mapper
        UserProfileDto base = userMapper.toDto(target);

        return UserRelationDto.builder()
                .id(base.getId())
                .displayName(base.getDisplayName())
                .avatarUrl(base.getAvatarUrl())
                .isActive(base.getIsActive())
                .bio(base.getBio())
                .favorites(base.getFavorites())
                .dateOfBirth(base.getDateOfBirth())
                .isFollowing(isFollowing)
                .isFollowedBy(isFollowedBy)
                .isFriend(isFriend)
                .build();
    }
}