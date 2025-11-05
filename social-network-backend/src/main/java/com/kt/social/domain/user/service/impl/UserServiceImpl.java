package com.kt.social.domain.user.service.impl;

import com.kt.social.auth.model.UserCredential;
import com.kt.social.auth.repository.UserCredentialRepository;
import com.kt.social.auth.util.SecurityUtils;
import com.kt.social.common.exception.BadRequestException;
import com.kt.social.common.exception.ResourceNotFoundException;
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

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends BaseFilterService<User, UserRelationDto> implements UserService {

    private final UserCredentialRepository userCredentialRepository;
    private final UserRepository userRepository;
    private final UserRelaRepository userRelaRepository;
    private final FriendshipRepository friendshipRepository;
    private final UserMapper userMapper;
    private final StorageService storageService;

    @Override
    public User getCurrentUser() {
        String username = SecurityUtils.getCurrentUsername()
                .orElseThrow(() -> new BadRequestException("User not authenticated or session expired"));

        User user = userRepository.findByCredentialUsername(username);
        if (user == null) {
            throw new ResourceNotFoundException("Not found user according to the token: " + username);
        }
        return user;
    }

    @Override
    public UserProfileDto getProfile(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return userMapper.toDto(user);
    }

    @Override
    @Transactional
    public UserProfileDto updateProfile(UpdateUserProfileRequest request) {
        var credential = SecurityUtils.getCurrentUserCredential(userCredentialRepository)
                .orElseThrow(() -> new BadRequestException("User not authenticated"));

        User user = userRepository.findById(credential.getUser().getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

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
            throw new BadRequestException("You cannot follow yourself");

        User follower = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        User following = userRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("Target user not found"));

        boolean exists = userRelaRepository.existsByFollowerAndFollowing(follower, following);
        if (exists) {
            throw new BadRequestException("Already following");
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
            throw new BadRequestException("You cannot unfollow yourself");
        }

        User follower = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        User following = userRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("Target user not found"));

        boolean exists = userRelaRepository.existsByFollowerAndFollowing(follower, following);
        if (!exists) {
            throw new BadRequestException("You are not following this user");
        }

        userRelaRepository.deleteByFollowerAndFollowing(follower, following);
        return new FollowResponse("Unfollowed successfully", false);
    }

    @Override
    @Transactional
    public FollowResponse removeFollower(Long currentUserId, Long followerId) {
        User current = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        User follower = userRepository.findById(followerId)
                .orElseThrow(() -> new ResourceNotFoundException("Follower not found"));

        if (!userRelaRepository.existsByFollowerAndFollowing(follower, current))
            throw new BadRequestException("This user is not following you");

        userRelaRepository.deleteByFollowerAndFollowing(follower, current);
        return new FollowResponse("Removed follower successfully", false);
    }

    @Override
    public UserProfileDto getProfileByUsername(String username) {
        UserCredential cred = userCredentialRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Credential not found"));
        User user = userRepository.findByCredential(cred);
        if (user == null) {
            throw new ResourceNotFoundException("Not found profile for user: " + username);
        }

        return userMapper.toDto(user);
    }

    @Transactional
    @Override
    public UserProfileDto updateAvatar(MultipartFile avatarFile) {
        var credential = SecurityUtils.getCurrentUserCredential(userCredentialRepository)
                .orElseThrow(() -> new BadRequestException("User not authenticated"));

        User user = userRepository.findById(credential.getUser().getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Xóa ảnh cũ (nếu có)
        if (user.getAvatarUrl() != null) {
            storageService.deleteFile(user.getAvatarUrl());
        }

        // Lưu ảnh mới vào folder avatars
        String avatarUrl = storageService.saveFile(avatarFile, "avatars");
        user.setAvatarUrl(avatarUrl);
        userRepository.save(user);

        return userMapper.toDto(user);
    }

    @Override
    public UserRelationDto getRelationWithUser(Long targetId) {
        User current = getCurrentUser();
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return mapToRelationDto(current, target);
    }

    // ---------------------- Search + Filter ----------------------
    @Override
    @Transactional(readOnly = true)
    public PageVO<UserRelationDto> searchUsers(String filter, Pageable pageable) {
        var currentUser = getCurrentUser();

        Specification<User> baseSpec = (root, query, cb) -> cb.and(
                cb.notEqual(root.get("id"), currentUser.getId()),
                cb.isTrue(root.get("isActive"))
        );

        Specification<User> combinedSpec = baseSpec;

        if (filter != null && !filter.isBlank()) {
            // 🧩 Nếu filter là RSQL (chứa ==, =like=, ...)
            if (filter.contains("==") || filter.contains("=like=")) {
                combinedSpec = combinedSpec.and(io.github.perplexhub.rsql.RSQLJPASupport.toSpecification(filter));
            } else {
                // Nếu chỉ là chuỗi tìm kiếm thông thường
                String likeFilter = "%" + filter.toLowerCase() + "%";

                Specification<User> keywordSpec = (root, query, cb) -> cb.or(
                        cb.like(cb.lower(root.get("displayName")), likeFilter),
                        cb.like(cb.lower(root.join("credential").get("username")), likeFilter),
                        cb.like(cb.lower(root.join("userInfo").get("bio")), likeFilter),
                        cb.like(cb.lower(root.join("userInfo").get("favorites")), likeFilter)
                );

                combinedSpec = combinedSpec.and(keywordSpec);
            }
        }

        Page<User> page = userRepository.findAll(combinedSpec, pageable);

        var content = page.getContent().stream()
                .map(u -> mapToRelationDto(currentUser, u))
                .toList();

        return PageVO.<UserRelationDto>builder()
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .numberOfElements(content.size())
                .content(content)
                .build();
    }

    // ---------------------- Follow/Unfollow ----------------------

    @Override
    @Transactional
    public PageVO<UserRelationDto> getFollowersPaged(Long userId, Pageable pageable) {
        User viewer = getCurrentUser();
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

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
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

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