package com.kt.social.domain.user.service.impl;

import com.kt.social.auth.enums.AccountStatus;
import com.kt.social.auth.model.Role;
import com.kt.social.auth.model.UserCredential;
import com.kt.social.auth.repository.RoleRepository;
import com.kt.social.auth.repository.UserCredentialRepository;
import com.kt.social.common.exception.AccessDeniedException;
import com.kt.social.common.exception.BadRequestException;
import com.kt.social.common.exception.ResourceNotFoundException;
import com.kt.social.common.service.BaseFilterService;
import com.kt.social.common.utils.BlockUtils;
import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.admin.dto.AdminUpdateUserRequest;
import com.kt.social.domain.admin.dto.AdminUserViewDto;
import com.kt.social.domain.audit.service.ActivityLogService;
import com.kt.social.domain.friendship.dto.FriendshipResponse;
import com.kt.social.domain.friendship.enums.FriendshipStatus;
import com.kt.social.domain.friendship.model.Friendship;
import com.kt.social.domain.friendship.repository.FriendshipRepository;
import com.kt.social.domain.user.dto.*;
import com.kt.social.domain.user.mapper.UserMapper;
import com.kt.social.domain.user.model.*;
import com.kt.social.domain.user.repository.*;
import com.kt.social.domain.user.service.UserService;
import com.kt.social.infra.storage.StorageService;
import io.github.perplexhub.rsql.RSQLJPASupport;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends BaseFilterService<User, UserRelationDto> implements UserService {

    private final BlockUtils blockUtils;
    private final UserCredentialRepository userCredentialRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRelaRepository userRelaRepository;
    private final FriendshipRepository friendshipRepository;
    private final UserMapper userMapper;
    private final StorageService storageService;
    private final ActivityLogService activityLogService;

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

//        activityLogService.logActivity(
//                user,
//                "USER:UPDATE_PROFILE",
//                "USER",
//                user.getId(),
//                null
//        );

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

//        activityLogService.logActivity(
//                follower,
//                "USER:FOLLOW",
//                "USER",
//                targetId,
//                null
//        );

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

//        activityLogService.logActivity(
//                follower,
//                "USER:UNFOLLOW",
//                "USER",
//                targetId,
//                null
//        );

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

//        activityLogService.logActivity(
//                current, // (Actor là 'current' - người thực hiện)
//                "USER:REMOVE_FOLLOWER",
//                "USER", // (Target là 'follower')
//                followerId,
//                null
//        );

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

//        activityLogService.logActivity(
//                user,
//                "USER:UPDATE_AVATAR",
//                "USER",
//                user.getId(),
//                Map.of("newAvatarUrl", avatarUrl)
//        );

        return userMapper.toDto(user);
    }

    @Override
    public UserRelationDto getRelationWithUser(Long targetId) {
        User current = getCurrentUser();
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return mapToRelationDto(current, target);
    }

    @Override
    @Transactional(readOnly = true)
    public PageVO<AdminUserViewDto> getAllUsers(String filter, Pageable pageable) {

        Specification<User> spec = Specification.where(null);
        if (filter != null && !filter.isBlank()) {
            String mappedFilter = filter
                    .replace("email", "credential.email")
                    .replace("username", "credential.username")
                    .replace("status", "credential.status")
                    .replace("role", "credential.roles.name")
                    .replace("bio", "userInfo.bio");
            spec = RSQLJPASupport.toSpecification(mappedFilter);
        }

        Pageable sortedPageable = mapSortProperties(pageable);

        Page<User> userPage = userRepository.findAll(spec, sortedPageable);

        List<AdminUserViewDto> content = userPage.getContent().stream()
                .map(userMapper::toAdminViewDto)
                .toList();

        return PageVO.<AdminUserViewDto>builder()
                .page(userPage.getNumber())
                .size(userPage.getSize())
                .totalElements(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .numberOfElements(content.size())
                .content(content)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUserViewDto getUserByIdAsAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return userMapper.toAdminViewDto(user);
    }

    @Override
    @Transactional
    public AdminUserViewDto updateUserAsAdmin(Long userId, AdminUpdateUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        UserCredential credential = user.getCredential();

        if (request.getDisplayName() != null) {
            user.setDisplayName(request.getDisplayName());
        }

        if (request.getBio() != null && user.getUserInfo() != null) {
            user.getUserInfo().setBio(request.getBio());
        }

//        if (request.getStatus() != null) {
//            credential.setStatus(request.getStatus());
//            // Nếu ban (cấm), set 'isActive' = false
//            if (request.getStatus() == AccountStatus.BLOCKED) {
//                user.setIsActive(false);
//            } else if (request.getStatus() == AccountStatus.ACTIVE) {
//                user.setIsActive(true);
//            }
//        }

        if (request.getRoles() != null && !request.getRoles().isEmpty()) {
            Set<Role> newRoles = new HashSet<>();
            for (String roleName : request.getRoles()) {
                Role role = roleRepository.findByName(roleName.toUpperCase())
                        .orElseThrow(() -> new BadRequestException("Role not found: " + roleName));
                newRoles.add(role);
            }
            credential.setRoles(newRoles);
        }

        User savedUser = userRepository.save(user);
        userCredentialRepository.save(credential);

        activityLogService.logActivity(
                getCurrentUser(),
                "USER:UPDATE_ANY",
                "User",
                savedUser.getId(),
                Map.of("changes", request.toString())
        );

        return userMapper.toAdminViewDto(savedUser);
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
//                cb1.isTrue(root1.get("isActive")),
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
    @Transactional(readOnly = true)
    public PageVO<UserRelationDto> getFollowersPaged(Long userId, String filter, Pageable pageable) {
        User viewer = getCurrentUser();
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        var blockedIds = blockUtils.getAllBlockedIds(viewer.getId());
        blockedIds.addAll(friendshipRepository.findBlockedUserIdsByTarget(viewer.getId()));

        Specification<UserRela> spec = (root, query, cb) -> {
            // Lấy ai đang follow Target
            // UserRela: follower -> following (Target)
            var predicate = cb.equal(root.get("following"), target);

            // Loại bỏ user bị block (Lọc ngay trong DB để đúng phân trang)
            if (!blockedIds.isEmpty()) {
                // Check ID của người Follower xem có nằm trong list block không
                predicate = cb.and(predicate, cb.not(root.get("follower").get("id").in(blockedIds)));
            }
            return predicate;
        };

        spec = spec.and(buildRelationFilterSpec(filter, "follower"));

        Page<UserRela> followersPage = userRelaRepository.findAll(spec, pageable);

        List<User> followers = followersPage.getContent().stream()
                .map(UserRela::getFollower)
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
    @Transactional(readOnly = true)
    public PageVO<UserRelationDto> getFollowingPaged(Long userId, String filter, Pageable pageable) {
        User viewer = getCurrentUser();
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        var blockedIds = blockUtils.getAllBlockedIds(viewer.getId());
        blockedIds.addAll(friendshipRepository.findBlockedUserIdsByTarget(viewer.getId()));

        Specification<UserRela> spec = (root, query, cb) -> {
            // Target đang follow ai
            // UserRela: follower (Target) -> following
            var predicate = cb.equal(root.get("follower"), target);

            // Loại bỏ block
            if (!blockedIds.isEmpty()) {
                // Check ID của người Following xem có nằm trong list block không
                predicate = cb.and(predicate, cb.not(root.get("following").get("id").in(blockedIds)));
            }
            return predicate;
        };

        spec = spec.and(buildRelationFilterSpec(filter, "following"));

        Page<UserRela> followingPage = userRelaRepository.findAll(spec, pageable);

        List<User> following = followingPage.getContent().stream()
                .map(UserRela::getFollowing)
                .toList();

        Map<Long, UserRelationDto> relationDtos = mapPageToRelationDtos(viewer, following);

        var content = following.stream()
                .map(u -> relationDtos.get(u.getId()))
                .toList();

        return PageVO.<UserRelationDto>builder()
                .page(followingPage.getNumber())
                .size(followingPage.getSize())
                .totalElements(followingPage.getTotalElements())
                .totalPages(followingPage.getTotalPages())
                .numberOfElements(content.size())
                .content(content)
                .build();
    }

    // ========== Hàm tiện ích chuyển đổi User → UserRelationDto ==========
    private Map<Long, UserRelationDto> mapPageToRelationDtos(User viewer, List<User> targets) {
        if (targets.isEmpty()) {
            return Map.of();
        }

        Set<Long> targetIds = targets.stream().map(User::getId).collect(Collectors.toSet());
        Long viewerId = viewer.getId();

        // Query 2 & 3: Giữ nguyên logic Following/Follower
        Set<Long> followingIds = userRelaRepository.findFollowingIds(viewerId, targetIds);
        Set<Long> followedByIds = userRelaRepository.findFollowerIds(viewerId, targetIds);

        // Query 4: SỬA LOGIC MAP
        // Lấy List<Friendship> (Entity) thay vì DTO ngay lập tức
        List<Friendship> friendships = friendshipRepository.findFriendshipsBetween(viewerId, targetIds);

        // Map<TargetId, FriendshipResponse>
        Map<Long, FriendshipResponse> friendshipMap = new HashMap<>();

        for (Friendship f : friendships) {
            Long senderId = f.getSender().getId();
            Long receiverId = f.getReceiver().getId();

            // Xác định ai là target (người kia) trong mối quan hệ này
            // Nếu Viewer là Sender -> Target là Receiver
            // Nếu Viewer là Receiver -> Target là Sender
            Long targetIdKey = senderId.equals(viewerId) ? receiverId : senderId;

            FriendshipResponse dto = FriendshipResponse.builder()
                    .status(f.getStatus()) // Ví dụ: FRIEND
                    .senderId(senderId)    // Luôn đúng: ID người gửi thực tế trong DB
                    .receiverId(receiverId)// Luôn đúng: ID người nhận thực tế trong DB
                    .build();

            // Convert sang DTO và put vào Map với key là TargetId
            friendshipMap.put(targetIdKey, dto);
        }

        // Map kết quả cuối cùng
        return targets.stream().map(target -> {
            Long targetId = target.getId();

            boolean isFollowing = followingIds.contains(targetId);
            boolean isFollowedBy = followedByIds.contains(targetId);

            // Lấy từ Map, nếu không có thì trả về trạng thái NONE
            FriendshipResponse friendship = friendshipMap.get(targetId);

            if (friendship == null) {
                // Tạo object mặc định nếu chưa có quan hệ
                friendship = FriendshipResponse.builder()
                        .senderId(viewerId)
                        .receiverId(targetId) // Lưu ý: receiver là target nếu là NONE (để mặc định mình gửi cho họ)
                        .status(FriendshipStatus.NONE)
                        .build();
            }

            UserProfileDto base = userMapper.toDto(target);

            return UserRelationDto.builder()
                    .id(base.getId())
                    .displayName(base.getDisplayName())
                    .avatarUrl(base.getAvatarUrl())
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
                        .senderId(f.getSender().getId())
                        .receiverId(f.getReceiver().getId())
                        .build())
                .orElse(FriendshipResponse.builder().build()); // Empty response if no friendship exists

        // Get full user profile from mapper
        UserProfileDto base = userMapper.toDto(target);

        return UserRelationDto.builder()
                .id(base.getId())
                .displayName(base.getDisplayName())
                .avatarUrl(base.getAvatarUrl())
                .bio(base.getBio())
                .favorites(base.getFavorites())
                .dateOfBirth(base.getDateOfBirth())
                .isFollowing(isFollowing)
                .isFollowedBy(isFollowedBy)
                .friendship(friendship) // Use the properly built friendship response
                .build();
    }

    /**
     * Helper: Tạo Spec lọc cho bảng UserRela nhưng dựa trên thuộc tính của User liên quan.
     * @param filter Chuỗi filter từ request
     * @param joinFieldName Tên trường cần join ("follower" hoặc "following")
     */
    private Specification<UserRela> buildRelationFilterSpec(String filter, String joinFieldName) {
        if (filter == null || filter.isBlank()) {
            return null;
        }

        // Trường hợp 1: Dùng RSQL (có dấu == hoặc =like=)
        if (filter.contains("==") || filter.contains("=like=")) {
            // Lưu ý: RSQLJPASupport mặc định map vào root entity (UserRela).
            // Để lọc được User, client cần gửi đúng path, ví dụ: "follower.displayName==abc"
            // Hoặc chúng ta có thể prefix thủ công nếu client chỉ gửi "displayName==abc",
            // nhưng cách đơn giản nhất là để RSQL xử lý native path.
            return RSQLJPASupport.toSpecification(filter);
        }

        // Trường hợp 2: Search keyword đơn giản (như searchUsers)
        else {
            String likeFilter = "%" + filter.toLowerCase() + "%";
            return (root, query, cb) -> {
                // Join sang bảng User (follower hoặc following)
                Join<UserRela, User> userJoin = root.join(joinFieldName, JoinType.INNER);

                // Tìm kiếm trên các trường của User đó
                return cb.or(
                        cb.like(cb.lower(userJoin.get("displayName")), likeFilter),
                        cb.like(cb.lower(userJoin.get("credential").get("username")), likeFilter),
                        cb.like(cb.lower(userJoin.get("userInfo").get("bio")), likeFilter)
                );
            };
        }
    }

    /**
     * Hàm Helper: Dịch lại các thuộc tính Sort sang đúng đường dẫn JPA
     */
    private Pageable mapSortProperties(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return pageable;
        }

        List<Sort.Order> newOrders = pageable.getSort().stream()
                .map(order -> {
                    String property = order.getProperty();
                    // Map các trường ảo sang trường thật trong DB
                    switch (property) {
                        case "email":
                            return new Sort.Order(order.getDirection(), "credential.email");
                        case "username":
                            return new Sort.Order(order.getDirection(), "credential.username");
                        case "status":
                            return new Sort.Order(order.getDirection(), "credential.status");
                        case "role":
                            return new Sort.Order(order.getDirection(), "credential.roles.name");
                        case "bio":
                            return new Sort.Order(order.getDirection(), "userInfo.bio");
                        case "favorites":
                            return new Sort.Order(order.getDirection(), "userInfo.favorites");
                        case "dateOfBirth":
                            return new Sort.Order(order.getDirection(), "userInfo.dateOfBirth");
                        default:
                            // Các trường gốc (id, displayName...) giữ nguyên
                            return order;
                    }
                })
                .toList();

        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(newOrders));
    }
}