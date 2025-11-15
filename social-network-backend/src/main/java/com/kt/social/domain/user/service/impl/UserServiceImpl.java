package com.kt.social.domain.user.service.impl;

import com.kt.social.auth.model.UserCredential;
import com.kt.social.auth.repository.UserCredentialRepository;
import com.kt.social.auth.util.SecurityUtils;
import com.kt.social.common.exception.AccessDeniedException;
import com.kt.social.common.exception.BadRequestException;
import com.kt.social.common.exception.ResourceNotFoundException;
import com.kt.social.common.service.BaseFilterService;
import com.kt.social.common.utils.BlockUtils;
import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.friendship.dto.FriendshipResponse;
import com.kt.social.domain.friendship.enums.FriendshipStatus;
import com.kt.social.domain.friendship.repository.FriendshipRepository;
import com.kt.social.domain.user.dto.*;
import com.kt.social.domain.user.mapper.UserMapper;
import com.kt.social.domain.user.model.*;
import com.kt.social.domain.user.repository.*;
import com.kt.social.domain.user.service.UserService;
import com.kt.social.infra.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends BaseFilterService<User, UserRelationDto> implements UserService {

    private final BlockUtils blockUtils;
    private final UserCredentialRepository userCredentialRepository;
    private final UserRepository userRepository;
    private final UserRelaRepository userRelaRepository;
    private final FriendshipRepository friendshipRepository;
    private final UserMapper userMapper;
    private final StorageService storageService;

    @Override
    public User getCurrentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null)
            throw new ResourceNotFoundException("Authentication missing");

        long userId;
        try {
            userId = Long.parseLong(auth.getName());
        } catch (NumberFormatException e) {
            throw new ResourceNotFoundException("Invalid principal id: " + auth.getName());
        }

        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Not found user according to the token: " + userId));
    }

    @Override
    public UserProfileDto getProfile(Long id) {
        User current = getCurrentUser();
        if (blockUtils.isBlocked(current.getId(), id) || blockUtils.isBlocked(id, current.getId())) {
            throw new AccessDeniedException("You cannot view this profile");
        }

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return userMapper.toDto(user);
    }

    @Override
    @Transactional
    public UserProfileDto updateProfile(UpdateUserProfileRequest request) {
        User user = getCurrentUser();

        // cập nhật thông tin text
        if (request.getDisplayName() != null && !request.getDisplayName().isEmpty())
            user.setDisplayName(request.getDisplayName());
        if (request.getBio() != null && !request.getBio().isEmpty() && user.getUserInfo() != null)
            user.getUserInfo().setBio(request.getBio());
        if (request.getFavorites() != null && !request.getFavorites().isEmpty() && user.getUserInfo() != null)
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
    public FollowResponse followUser(Long targetId) {
        User follower = getCurrentUser();
        Long userId = follower.getId();

        if (userId.equals(targetId))
            throw new BadRequestException("You cannot follow yourself");

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
    public FollowResponse unfollowUser(Long targetId) {
        User follower = getCurrentUser();
        Long userId = follower.getId();

        if (userId.equals(targetId)) {
            throw new BadRequestException("You cannot unfollow yourself");
        }
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
    public FollowResponse removeFollower(Long followerId) {
        User current = getCurrentUser();
        User follower = userRepository.findById(followerId)
                .orElseThrow(() -> new ResourceNotFoundException("Follower not found"));

        if (!userRelaRepository.existsByFollowerAndFollowing(follower, current))
            throw new BadRequestException("This user is not following you");

        userRelaRepository.deleteByFollowerAndFollowing(follower, current);
        return new FollowResponse("Removed follower successfully", false);
    }

    @Transactional
    @Override
    public UserProfileDto updateAvatar(MultipartFile avatarFile) {
        User user = getCurrentUser();

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
        var viewer = getCurrentUser();

        var blockedByMe = blockUtils.getAllBlockedIds(viewer.getId());
        var blockedMe = friendshipRepository.findBlockedUserIdsByTarget(viewer.getId());
        var totalBlocked = new HashSet<>(blockedByMe);
        totalBlocked.addAll(blockedMe);

        Specification<User> combinedSpec = (root1, query1, cb1) -> cb1.and(
                cb1.notEqual(root1.get("id"), viewer.getId()),
                cb1.isTrue(root1.get("isActive")),
                totalBlocked.isEmpty() ? cb1.conjunction() : cb1.not(root1.get("id").in(totalBlocked))
        );

        if (filter != null && !filter.isBlank()) {
            if (filter.contains("==") || filter.contains("=like=")) {
                combinedSpec = combinedSpec.and(io.github.perplexhub.rsql.RSQLJPASupport.toSpecification(filter));
            } else {
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
        List<User> targets = page.getContent();

        // 4. Lấy dữ liệu quan hệ (N+1 FIX)
        Map<Long, UserRelationDto> relationDtos = mapPageToRelationDtos(viewer, targets);

        // 5. Map lại theo đúng thứ tự
        var content = targets.stream()
                .map(u -> relationDtos.get(u.getId()))
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

        var blockedIds = blockUtils.getAllBlockedIds(viewer.getId());
        blockedIds.addAll(friendshipRepository.findBlockedUserIdsByTarget(viewer.getId()));

        var followersPage = userRelaRepository.findByFollowing(target, pageable);

        List<User> followers = followersPage.getContent().stream()
                .map(UserRela::getFollower)
                .filter(u -> !blockedIds.contains(u.getId()))
                .toList();

        Map<Long, UserRelationDto> relationDtos = mapPageToRelationDtos(viewer, followers);

        var content = followers.stream()
                .map(u -> relationDtos.get(u.getId()))
                .toList();

        return PageVO.<UserRelationDto>builder()
                .page(followersPage.getNumber())
                .size(followersPage.getSize())
                .totalElements(followersPage.getTotalElements())
                .totalPages(followersPage.getTotalPages())
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

        var blockedIds = blockUtils.getAllBlockedIds(viewer.getId());
        blockedIds.addAll(friendshipRepository.findBlockedUserIdsByTarget(viewer.getId()));

        var followingPaged = userRelaRepository.findByFollower(target, pageable);

        List<User> following = followingPaged.getContent().stream()
                .map(UserRela::getFollowing)
                .filter(u -> !blockedIds.contains(u.getId()))
                .toList();

        Map<Long, UserRelationDto> relationDtos = mapPageToRelationDtos(viewer, following);

        var content = following.stream()
                .map(u -> relationDtos.get(u.getId()))
                .toList();

        return PageVO.<UserRelationDto>builder()
                .page(followingPaged.getNumber())
                .size(followingPaged.getSize())
                .totalElements(followingPaged.getTotalElements())
                .totalPages(followingPaged.getTotalPages())
                .numberOfElements(content.size())
                .content(content)
                .build();
    }

    // ========== Hàm tiện ích chuyển đổi User → UserRelationDto ==========
    private Map<Long, UserRelationDto> mapPageToRelationDtos(User viewer, List<User> targets) {
        if (targets.isEmpty()) {
            return Map.of();
        }

        // Lấy danh sách ID của các user mục tiêu
        Set<Long> targetIds = targets.stream().map(User::getId).collect(Collectors.toSet());
        Long viewerId = viewer.getId();

        // Query 2: Lấy trạng thái following (Viewer đang follow ai trong list?)
        Set<Long> followingIds = userRelaRepository.findFollowingIds(viewerId, targetIds);

        // Query 3: Lấy trạng thái followed by (Ai trong list đang follow Viewer?)
        Set<Long> followedByIds = userRelaRepository.findFollowerIds(viewerId, targetIds);

        // Query 4: Lấy trạng thái bạn bè
        Map<Long, FriendshipResponse> friendshipMap = friendshipRepository
                .findFriendshipsBetween(viewerId, targetIds)
                .stream()
                .map(f -> FriendshipResponse.from(f, viewerId)) // Cần 1 helper 'from'
                .collect(Collectors.toMap(
                        fr -> fr.getReceiverId().equals(viewerId) ? fr.getSenderId() : fr.getReceiverId(),
                        fr -> fr
                ));

        // Bây giờ map trong bộ nhớ (cực nhanh, không tốn query)
        return targets.stream().map(target -> {
            Long targetId = target.getId();

            boolean isFollowing = followingIds.contains(targetId);
            boolean isFollowedBy = followedByIds.contains(targetId);

            // Lấy friendship, hoặc tạo rỗng nếu không có
            FriendshipResponse friendship = friendshipMap.getOrDefault(targetId,
                    FriendshipResponse.builder()
                            .senderId(viewerId)
                            .receiverId(targetId)
                            .status(FriendshipStatus.NONE) // Cần enum 'NONE'
                            .build()
            );

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
                    .friendship(friendship)
                    .build();
        }).collect(Collectors.toMap(UserRelationDto::getId, dto -> dto));
    }

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