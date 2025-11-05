package com.kt.social.domain.post.service.impl;

import com.kt.social.auth.repository.UserCredentialRepository;
import com.kt.social.auth.util.SecurityUtils;
import com.kt.social.common.exception.AccessDeniedException;
import com.kt.social.common.exception.BadRequestException;
import com.kt.social.common.exception.ResourceNotFoundException;
import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.friendship.enums.FriendshipStatus;
import com.kt.social.domain.friendship.repository.FriendshipRepository;
import com.kt.social.domain.post.dto.PostRequest;
import com.kt.social.domain.post.dto.PostResponse;
import com.kt.social.domain.post.enums.AccessScope;
import com.kt.social.domain.post.mapper.PostMapper;
import com.kt.social.domain.post.model.Post;
import com.kt.social.domain.post.repository.PostRepository;
import com.kt.social.domain.post.service.PostFilterService;
import com.kt.social.domain.post.service.PostService;
import com.kt.social.domain.react.enums.TargetType;
import com.kt.social.domain.react.service.ReactService;
import com.kt.social.domain.user.model.User;
import com.kt.social.domain.user.model.UserRela;
import com.kt.social.domain.user.repository.UserRelaRepository;
import com.kt.social.domain.user.repository.UserRepository;
import com.kt.social.domain.user.service.UserService;
import com.kt.social.infra.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    private final UserRelaRepository userRelaRepository;
    private final UserCredentialRepository credRepo;
    private final FriendshipRepository friendshipRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final PostFilterService postFilterService;
    private final UserService userService;
    private final ReactService reactService;
    private final PostMapper postMapper;

    @Override
    @Transactional
    public PostResponse create(PostRequest request) {
        User author = SecurityUtils.getCurrentUser(credRepo, userRepository);

        String mediaUrl = null;

        if (request.getMedia() != null && !request.getMedia().isEmpty()) {
            mediaUrl = storageService.saveFile(request.getMedia(), "posts");
        }

        Post sharedPost = Optional.ofNullable(request.getSharedPostId())
                .flatMap(postRepository::findById)
                .orElse(null);

        Post post = Post.builder()
                .author(author)
                .content(request.getContent())
                .mediaUrl(mediaUrl)
                .accessModifier(request.getAccessModifier())
                .sharedPost(sharedPost)
                .createdAt(Instant.now())
                .build();

        postRepository.save(post);
        return postMapper.toDto(post);
    }

    @Override
    @Transactional
    public PostResponse update(PostRequest request) {
        User currentUser = SecurityUtils.getCurrentUser(credRepo, userRepository);

        Post post = postRepository.findById(request.getPostId())
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        if (!post.getAuthor().getId().equals(currentUser.getId())) {
            throw new BadRequestException("You are not authorized to update this post.");
        }

        post.setContent(request.getContent());

        if (request.getAccessModifier() != null) {
            post.setAccessModifier(request.getAccessModifier());
        }

        // Xóa media cũ nếu được yêu cầu
        if (Boolean.TRUE.equals(request.getRemoveMedia()) && post.getMediaUrl() != null) {
            storageService.deleteFile(post.getMediaUrl());
            post.setMediaUrl(null);
        }

        // Upload media mới nếu có
        if (request.getMedia() != null && !request.getMedia().isEmpty()) {
            if (post.getMediaUrl() != null && request.getRemoveMedia()) {
                storageService.deleteFile(post.getMediaUrl());
            }
            String mediaUrl = storageService.saveFile(request.getMedia(), "posts");
            post.setMediaUrl(mediaUrl);
        }

        post.setUpdatedAt(Instant.now());

        postRepository.save(post);
        return postMapper.toDto(post);
    }

    @Override
    @Transactional(readOnly = true)
    public PostResponse getPostById(Long postId) {
        User viewer = SecurityUtils.getCurrentUser(credRepo, userRepository);

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found with id: " + postId));

        User author = post.getAuthor();

        // Kiểm tra quyền truy cập
        switch (post.getAccessModifier()) {
            case PRIVATE -> {
                // Chỉ tác giả mới xem được
                if (!viewer.getId().equals(author.getId())) {
                    throw new AccessDeniedException("You don't have permission to view this private post");
                }
            }
            case FRIENDS -> {
                boolean areFriends = friendshipRepository.existsBySenderAndReceiverAndStatus(author, viewer, FriendshipStatus.FRIEND)
                        || friendshipRepository.existsBySenderAndReceiverAndStatus(viewer, author, FriendshipStatus.FRIEND);

                if (!areFriends && !viewer.getId().equals(author.getId())) {
                    throw new AccessDeniedException("Only friends can view this post");
                }
            }
            case PUBLIC -> {
                // Ai cũng xem được
            }
        }

        // Nếu bài này là bài chia sẻ thì kiểm tra luôn quyền xem bài gốc
        if (post.getSharedPost() != null) {
            boolean canViewShared = canViewSharedPost(viewer, post.getSharedPost());
            if (!canViewShared) {
                // Ẩn bài gốc (UI sẽ hiển thị "Bài gốc không khả dụng")
                post.setSharedPost(null);
            }
        }

        PostResponse dto = postMapper.toDto(post);

        Long currentUserId = userService.getCurrentUser().getId();
        dto.setReactSummary(reactService.getReactSummary(postId, TargetType.POST, currentUserId));

        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public PageVO<PostResponse> getUserPosts(Long userId, Pageable pageable) {
        User viewer = SecurityUtils.getCurrentUser(credRepo, userRepository);
        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Page<Post> page = postRepository.findByAuthor(targetUser, pageable);

        return getPostResponsePageVO(viewer, page);
    }

    @Override
    @Transactional(readOnly = true)
    public PageVO<PostResponse> getMyPosts(Pageable pageable) {
        User current = SecurityUtils.getCurrentUser(credRepo, userRepository);
        Page<Post> page = postRepository.findByAuthor(current, pageable);

        return getPostResponsePageVO(current, page);
    }

    @Override
    @Transactional
    public PostResponse sharePost(Long originalPostId, String caption) {
        User currentUser = SecurityUtils.getCurrentUser(credRepo, userRepository);
        Post original = postRepository.findById(originalPostId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        // Kiểm tra quyền share
        switch (original.getAccessModifier()) {
            case PRIVATE -> throw new BadRequestException("This post is private and cannot be shared.");
            case FRIENDS -> {
                boolean areFriends = friendshipRepository.existsByUserAndFriendAndStatusApproved(
                        currentUser, original.getAuthor()
                ) || friendshipRepository.existsByUserAndFriendAndStatusApproved(
                        original.getAuthor(), currentUser
                );
                if (!areFriends) {
                    throw new BadRequestException("You must be friends with the author to share this post.");
                }
            }
            default -> {} // PUBLIC => được phép
        }

        Post shared = Post.builder()
                .author(currentUser)
                .content(caption)
                .sharedPost(original)
                .accessModifier(AccessScope.PUBLIC)
                .build();

        postRepository.save(shared);
        return postMapper.toDto(shared);
    }

    @Override
    @Transactional(readOnly = true)
    public PageVO<PostResponse> getFeed(Pageable pageable, String filter) {
        User current = SecurityUtils.getCurrentUser(credRepo, userRepository);

        // Lấy danh sách bạn bè (hai chiều)
        var friends = friendshipRepository.findAllAcceptedFriends(current);

        // Lấy danh sách người mình theo dõi
        var followings = userRelaRepository.findByFollower(current)
                .stream()
                .map(UserRela::getFollowing)
                .toList();

        // Gộp tất cả ID hợp lệ (bao gồm chính current)
        var authorIds = Stream.concat(
                        Stream.concat(friends.stream(), followings.stream()),
                        Stream.of(current)
                )
                .map(User::getId)
                .distinct()
                .toList();

        // BaseSpec — lọc các bài viết của user hợp lệ
        Specification<Post> baseSpec = (root, query, cb) -> {
            query.distinct(true); // tránh duplicate khi join
            return root.get("author").get("id").in(authorIds);
        };

        // Gọi BaseFilterService (qua postFilterService)
        PageVO<PostResponse> pageVO = postFilterService.filterEntity(
                Post.class,
                (filter == null || filter.isBlank()) ? null : filter,
                pageable,
                postRepository,
                post -> {
                    if (!canViewPost(current, post)) return null; // 🚫 Bỏ qua bài không có quyền xem
                    return toResponseWithAccessCheck(current, post);
                },
                baseSpec
        );

        return pageVO;
    }

    @Override
    @Transactional
    public void deletePost(Long postId) {
        User currentUser = SecurityUtils.getCurrentUser(credRepo, userRepository);

        Post original = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        // Kiểm tra quyền
        boolean isAuthor = original.getAuthor().getId().equals(currentUser.getId());
//        boolean isAdmin = currentUser.getRoles().stream()
//                .anyMatch(role -> role.getName().equalsIgnoreCase("ROLE_ADMIN"));

        if (!isAuthor) {
            throw new AccessDeniedException("You are not authorized to delete this post.");
        }

        // Hủy liên kết các bài share
        List<Post> shares = postRepository.findBySharedPost(original);
        shares.forEach(shared -> shared.setSharedPost(null));
        postRepository.saveAll(shares);

        // Xóa file media nếu có
        Optional.ofNullable(original.getMediaUrl())
                .ifPresent(storageService::deleteFile);

        postRepository.delete(original);
    }

    // --------------------- Helper methods -------------------------

    public boolean canViewSharedPost(User viewer, Post original) {
        if (original == null) return false;
        if (viewer.getId().equals(original.getAuthor().getId())) return true;

        return switch (original.getAccessModifier()) {
            case PUBLIC -> true;
            case FRIENDS -> friendshipRepository.existsBySenderAndReceiverAndStatus(viewer, original.getAuthor(), FriendshipStatus.ACCEPTED)
                    || friendshipRepository.existsBySenderAndReceiverAndStatus(original.getAuthor(), viewer, FriendshipStatus.ACCEPTED);
            default -> false;
        };
    }

    private boolean canViewPost(User viewer, Post post) {
        if (post == null) return false;
        User author = post.getAuthor();
        if (viewer.getId().equals(author.getId())) return true; // Tác giả luôn xem được

        return switch (post.getAccessModifier()) {
            case PUBLIC -> true;
            case FRIENDS -> friendshipRepository.existsBySenderAndReceiverAndStatus(viewer, author, FriendshipStatus.ACCEPTED)
                    || friendshipRepository.existsBySenderAndReceiverAndStatus(author, viewer, FriendshipStatus.ACCEPTED);
            case PRIVATE -> false;
        };
    }

    @Transactional(readOnly = true)
    public Optional<Post> getVisibleSharedPost(User viewer, Post sharedPost) {
        if (sharedPost == null || sharedPost.getId() == null) return Optional.empty();
        return canViewSharedPost(viewer, sharedPost) ? Optional.of(sharedPost) : Optional.empty();
    }

    @Transactional(readOnly = true)
    protected PostResponse toResponseWithAccessCheck(User viewer, Post post) {
        PostResponse dto = postMapper.toDto(post);

        // kiểm tra bài share có khả dụng không
        if (post.getSharedPost() != null) {
            Optional<Post> visible = getVisibleSharedPost(viewer, post.getSharedPost());
            dto.setSharedPost(visible.map(postMapper::toDto).orElse(null));
        } else {
            dto.setSharedPost(null);
        }

        Long currentUserId = viewer.getId();
        dto.setReactSummary(reactService.getReactSummary(post.getId(), TargetType.POST, currentUserId));

        return dto;
    }

    @Transactional
    protected PageVO<PostResponse> getPostResponsePageVO(User current, Page<Post> page) {
        List<PostResponse> visiblePosts = page.stream()
                .map(post -> toResponseWithAccessCheck(current, post))
                .filter(Objects::nonNull)
                .toList();

        return PageVO.<PostResponse>builder()
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .numberOfElements(visiblePosts.size())
                .content(visiblePosts)
                .build();
    }
}