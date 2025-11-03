package com.kt.social.domain.user.service.impl;

import com.kt.social.auth.model.UserCredential;
import com.kt.social.auth.repository.UserCredentialRepository;
import com.kt.social.auth.util.SecurityUtils;
import com.kt.social.common.service.BaseFilterService;
import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.friendship.dto.FriendshipResponse;
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
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends BaseFilterService<User, UserProfileDto> implements UserService {

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
    public UserRelationDto getRelationWithUser(Long targetId) {
        User current = getCurrentUser();
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return mapToRelationDto(current, target);
    }

    // ---------------------- Search + Filter ----------------------

    @Override
    @Transactional(readOnly = true)
    public PageVO<UserProfileDto> searchUsers(String filter, Pageable pageable) {
        // baseSpec: loại bỏ người dùng bị khóa hoặc chính mình
        var currentUser = getCurrentUser();
        Specification<User> base = (root, q, cb) -> cb.and(
                cb.notEqual(root.get("id"), currentUser.getId()),
                cb.isTrue(root.get("isActive"))
        );

        return filterEntity(User.class, filter, pageable, userRepository, userMapper::toDto, base);
    }

    // ---------------------- Follow/Unfollow ----------------------

    @Override
    @Transactional
    public PageVO<UserRelationDto> getFollowersPaged(Long userId, Pageable pageable) {
        User viewer = getCurrentUser();
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        var followers = userRelaRepository.findByFollowing(target, pageable);
        var content = followers.getContent().stream()
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

    @Override
    @Transactional
    public PageVO<UserRelationDto> getFollowingPaged(Long userId, Pageable pageable) {
        User viewer = getCurrentUser();
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        var following = userRelaRepository.findByFollower(target, pageable);
        var content = following.getContent().stream()
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

    // ========== Hàm tiện ích chuyển đổi User → UserRelationDto ==========
    private UserRelationDto mapToRelationDto(User viewer, User target) {
        boolean isFollowing = userRelaRepository.existsByFollowerAndFollowing(viewer, target);
        boolean isFollowedBy = userRelaRepository.existsByFollowerAndFollowing(target, viewer);

        var friendship = friendshipRepository.findBySenderAndReceiver(viewer, target)
                .or(() -> friendshipRepository.findBySenderAndReceiver(target, viewer))
                .map(f -> FriendshipResponse.builder()
                        .status(f.getStatus())
                        .senderId(viewer.getId())
                        .receiverId(target.getId())
                        .build())
                .orElse(FriendshipResponse.builder().build()); // Empty response if no friendship exists

        // Get full user profile from mapper
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
                .friendship(friendship) // Use the properly built friendship response
                .build();
    }
}