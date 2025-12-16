package com.kt.social.domain.post.service.impl;

import com.kt.social.auth.model.Role;
import com.kt.social.common.exception.AccessDeniedException;
import com.kt.social.common.exception.BadRequestException;
import com.kt.social.common.exception.ResourceNotFoundException;
import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.audit.service.ActivityLogService;
import com.kt.social.domain.friendship.repository.FriendshipRepository;
import com.kt.social.domain.moderation.event.ContentCreatedEvent;
import com.kt.social.domain.post.dto.PostResponse;
import com.kt.social.domain.post.dto.UpdatePostRequest;
import com.kt.social.domain.post.enums.AccessScope;
import com.kt.social.domain.post.mapper.PostMapper;
import com.kt.social.domain.post.model.Post;
import com.kt.social.domain.post.repository.PostRepository;
import com.kt.social.domain.post.service.PostService;
import com.kt.social.domain.post.service.PostSyncService;
import com.kt.social.domain.react.dto.ReactSummaryDto;
import com.kt.social.domain.react.enums.TargetType;
import com.kt.social.domain.react.service.ReactService;
import com.kt.social.domain.user.model.User;
import com.kt.social.domain.user.model.UserRela;
import com.kt.social.domain.user.repository.UserRelaRepository;
import com.kt.social.domain.user.repository.UserRepository;
import com.kt.social.domain.user.service.UserService;
import com.kt.social.infra.ai.AiServiceClient;
import com.kt.social.infra.storage.StorageService;
import io.github.perplexhub.rsql.RSQLJPASupport;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    private final ApplicationEventPublisher eventPublisher;
    private final ActivityLogService activityLogService;
    private final UserRelaRepository userRelaRepository;
    private final FriendshipRepository friendshipRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final UserService userService;
    private final ReactService reactService;
    private final PostMapper postMapper;
    private final PostSyncService postSyncService;
    private final AiServiceClient aiServiceClient;

    @Override
    @Transactional
    public PostResponse create(String content, String accessModifier, List<MultipartFile> mediaFiles) {
        User author = userService.getCurrentUser();

        List<Map<String, String>> mediaList = List.of();
        if (mediaFiles != null && !mediaFiles.isEmpty()) {
            mediaList = mediaFiles.stream().map(file -> {
                String url = storageService.saveFile(file, "posts");
                String ext = getExtension(Objects.requireNonNull(file.getOriginalFilename()));
                String type = isVideo(ext) ? "video" : "image";
                return Map.of("type", type, "url", url);
            }).toList();
        }

        Post post = Post.builder()
                .author(author)
                .content(content)
                .media(mediaList)
                .accessModifier(accessModifier != null
                        ? AccessScope.valueOf(accessModifier)
                        : AccessScope.PUBLIC)
                .createdAt(Instant.now())
                .isSystemBan(false)
                .build();

        Post saved = postRepository.save(post);

//        activityLogService.logActivity(
//                author,
//                "POST:CREATE",
//                "Post",
//                savedPost.getId(),
//                Map.of("accessScope", savedPost.getAccessModifier().toString())
//        );

        if (saved.getAccessModifier() != AccessScope.PRIVATE) {
            postSyncService.syncPostToMilvus(saved.getId(), author.getId(), content);
        }

        eventPublisher.publishEvent(new ContentCreatedEvent(
                saved.getId(),
                TargetType.POST,
                saved.getContent(),
                saved.getAuthor().getId(),
                saved.getMedia()
        ));

        PostResponse dto = postMapper.toDto(saved);
        dto.setReactSummary(ReactSummaryDto.builder().build());
        dto.setShareCount(0);
        return dto;    }

    @Override
    @Transactional
    public PostResponse update(UpdatePostRequest request) {

        User currentUser = userService.getCurrentUser();
        Post post = postRepository.findById(request.getPostId())
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        boolean isOwner = post.getAuthor().getId().equals(currentUser.getId());
        boolean canUpdateAny = currentUserHasAuthority("POST:UPDATE");

        if (!isOwner && !canUpdateAny) {
            throw new AccessDeniedException("You are not authorized to update this post.");
        }

        post.setContent(request.getContent());
        if (request.getAccessModifier() != null) {
            post.setAccessModifier(AccessScope.valueOf(request.getAccessModifier()));
        }

        List<Map<String, String>> mediaList = new ArrayList<>();

        // Giữ lại media cũ
        if (request.getKeepMediaUrls() != null) {
            for (String url : request.getKeepMediaUrls()) {
                mediaList.add(Map.of("type", getTypeFromUrl(url), "url", url));
            }
        }

        // Xóa media cũ
        if (request.getRemoveMediaUrls() != null) {
            request.getRemoveMediaUrls().forEach(storageService::deleteFile);
        }

        // Thêm media mới
        if (request.getMediaFiles() != null && !request.getMediaFiles().isEmpty()) {
            List<Map<String, String>> uploaded = request.getMediaFiles().stream()
                    .map(file -> {
                        String url = storageService.saveFile(file, "posts");
                        String ext = getExtension(Objects.requireNonNull(file.getOriginalFilename()));
                        String type = isVideo(ext) ? "video" : "image";
                        return Map.of("type", type, "url", url);
                    }).toList();
            mediaList.addAll(uploaded);
        }

        post.setMedia(mediaList);
        post.setUpdatedAt(Instant.now());
        Post saved = postRepository.save(post);

        eventPublisher.publishEvent(new ContentCreatedEvent(
                saved.getId(),
                TargetType.POST,
                saved.getContent(),
                saved.getAuthor().getId(),
                saved.getMedia()
        ));

//        activityLogService.logActivity(
//                currentUser,
//                "POST:UPDATE",
//                "Post",
//                savedPost.getId(),
//                Map.of("newAccessScope", savedPost.getAccessModifier().toString())
//        );

        return toDtoWithReactsAndShares(saved, currentUser.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public PostResponse getPostById(Long postId) {
        User viewer = userService.getCurrentUser();
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found with id: " + postId));

        checkViewPermission(viewer, post);

        return toDtoWithReactsAndShares(post, viewer.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public PageVO<PostResponse> getUserPosts(Long userId, Pageable pageable) {
        User viewer = userService.getCurrentUser(); // <-- SỬA
        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Specification<Post> spec = (root, query, cb) -> {
            Predicate authorMatch = cb.equal(root.get("author"), targetUser);

            if (viewer.getId().equals(targetUser.getId())) {
                return authorMatch;
            }

            boolean areFriends = friendshipRepository.existsActiveFriendship(viewer, targetUser);

            Predicate publicPosts = cb.equal(root.get("accessModifier"), AccessScope.PUBLIC);

            if (areFriends) {
                Predicate friendPosts = cb.equal(root.get("accessModifier"), AccessScope.FRIENDS);
                return cb.and(authorMatch, cb.or(publicPosts, friendPosts));
            }

            return cb.and(authorMatch, publicPosts);
        };

        Page<Post> page = postRepository.findAll(spec, pageable);

        return getPostResponsePageVO(viewer, page);
    }

    @Override
    @Transactional(readOnly = true)
    public PageVO<PostResponse> getMyPosts(Pageable pageable) {
        User current = userService.getCurrentUser();
        return getUserPosts(current.getId(), pageable);
    }

    @Override
    @Transactional
    public PostResponse sharePost(Long originalPostId, String caption, AccessScope accessScope) {
        User currentUser = userService.getCurrentUser(); // <-- SỬA
        Post original = postRepository.findById(originalPostId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        checkViewPermission(currentUser, original);

        if (original.getAccessModifier() == AccessScope.PRIVATE) {
            throw new BadRequestException("This post is private and cannot be shared.");
        }

        Post shared = Post.builder()
                .author(currentUser)
                .content(caption)
                .sharedPost(original)
                .accessModifier(accessScope)
                .createdAt(Instant.now())
                .build();

        Post savedSharedPost = postRepository.save(shared);

//        activityLogService.logActivity(
//                currentUser,
//                "POST:SHARE",
//                "Post",
//                savedSharedPost.getId(),
//                Map.of("originalPostId", originalPostId)
//        );

        return toDtoWithReactsAndShares(savedSharedPost, currentUser.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public PageVO<PostResponse> getFeed(Pageable pageable, String filter) {
        User current = userService.getCurrentUser();

        // 1. Lấy danh sách bạn bè (bao gồm bản thân)
        var friends = friendshipRepository.findAllAcceptedFriends(current);
        List<Long> friendAndSelfIds = Stream.concat(friends.stream(), Stream.of(current))
                .map(User::getId)
                .distinct()
                .toList();

        // 2. Lấy danh sách đang theo dõi
        var followings = userRelaRepository.findByFollower(current)
                .stream()
                .map(UserRela::getFollowing)
                .toList();

        // 3. Tổng hợp danh sách tác giả được phép xem (Bạn bè + Bản thân + Đang theo dõi)
        List<Long> authorIds = Stream.concat(
                        friendAndSelfIds.stream(),
                        followings.stream().map(User::getId)
                )
                .distinct()
                .toList();

        // Safety check: Nếu list rỗng (hiếm khi xảy ra vì luôn có current user), return rỗng luôn
//        if (authorIds.isEmpty()) {
//            return PageVO.builder()
//                    .content(List.of(PostResponse ))
//                    .page(pageable.getPageNumber())
//                    .size(pageable.getPageSize())
//                    .totalElements(0L)
//                    .totalPages(0)
//                    .numberOfElements(0)
//                    .build();
//        }

        // 4. Build Specification
        Specification<Post> baseSpec = (root, query, cb) -> {
            // query.distinct(true); // Thường không cần thiết nếu logic IN chuẩn, bỏ đi cho nhẹ query

            // Điều kiện 1: Tác giả phải nằm trong danh sách quan hệ
            Predicate authorPredicate = root.get("author").get("id").in(authorIds);

            // Điều kiện 2: PUBLIC - Ai trong list authorIds đăng public thì mình đều thấy
            Predicate publicPosts = cb.equal(root.get("accessModifier"), AccessScope.PUBLIC);

            // Điều kiện 3: FRIENDS - Chỉ thấy nếu tác giả là Bạn bè hoặc chính mình
            Predicate friendPosts = cb.and(
                    cb.equal(root.get("accessModifier"), AccessScope.FRIENDS),
                    root.get("author").get("id").in(friendAndSelfIds)
            );

            // Điều kiện 4: PRIVATE - Chỉ thấy của chính mình
            Predicate privatePosts = cb.and(
                    cb.equal(root.get("accessModifier"), AccessScope.PRIVATE),
                    cb.equal(root.get("author").get("id"), current.getId()) // So sánh ID an toàn hơn object
            );

            // Điều kiện 5: Chưa bị xóa (SỬA LỖI TẠI ĐÂY)
            Predicate unviolentPosts = cb.isNull(root.get("deletedAt")); // ✅ Dùng isNull thay vì equal(null)

            // Tổng hợp Access Logic: (Public OR (Friend & là bạn) OR (Private & là mình))
            Predicate accessPredicate = cb.or(publicPosts, friendPosts, privatePosts);

            return cb.and(authorPredicate, accessPredicate, unviolentPosts);
        };

        // 5. Kết hợp với Filter từ Client (nếu có)
        Specification<Post> finalSpec = baseSpec;
        if (filter != null && !filter.isBlank()) {
            try {
                finalSpec = finalSpec.and(RSQLJPASupport.toSpecification(filter));
            } catch (Exception e) {
                // Log warning nếu filter sai cú pháp, tránh crash API
                // log.warn("Invalid filter: {}", filter);
            }
        }

        Page<Post> page = postRepository.findAll(finalSpec, pageable);

        return getPostResponsePageVO(current, page);
    }

    @Override
    @Transactional
    public void deletePost(Long postId) {
        User currentUser = userService.getCurrentUser();

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        boolean isOwner = post.getAuthor().getId().equals(currentUser.getId());
        boolean canDelete = currentUserHasAuthority("POST:DELETE_ANY");

        if (!(isOwner || canDelete)) {
            throw new AccessDeniedException("You are not authorized to delete this post.");
        }

        List<Post> shares = postRepository.findBySharedPost(post);
        shares.forEach(shared -> shared.setSharedPost(null));
        postRepository.saveAll(shares);

        postRepository.delete(post);

//        activityLogService.logActivity(
//                currentUser,
//                "POST:DELETE",
//                "Post",
//                postId,
//                Map.of("deletedPostAuthorId", post.getAuthor().getId())
//        );
    }

    // --------------------- Helper methods -------------------------

    private boolean currentUserHasAuthority(String authority) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return false;
        }

        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(authority));
    }

    @Transactional(readOnly = true)
    public PageVO<PostResponse> getPostResponsePageVO(User viewer, Page<Post> page) {
        List<Post> posts = page.getContent();
        if (posts.isEmpty()) {
            return PageVO.emptyPage(page); // Trả về trang rỗng
        }

        List<Long> postIds = posts.stream().map(Post::getId).toList();
        Long viewerId = viewer.getId();

        Map<Long, ReactSummaryDto> reactMap = reactService.getReactSummaries(postIds, viewerId, TargetType.POST);


        Map<Long, Integer> shareCountMap = postRepository.findShareCounts(postIds);

        List<Post> sharedPosts = posts.stream()
                .map(Post::getSharedPost)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, Post> sharedPostMap = sharedPosts.stream()
                .collect(Collectors.toMap(Post::getId, Function.identity(), (a, b) -> a)); // (Map<SharedPostId, SharedPost>)


        Map<Long, PostResponse> visibleSharedPostDtoMap = sharedPosts.stream()
                .filter(sp -> canViewPost(viewer, sp)) // Chỉ giữ lại post xem được
                .map(sp -> toDtoWithReactsAndShares(sp, viewerId)) // Map post xem được
                .collect(Collectors.toMap(PostResponse::getId, Function.identity()));

        // --- KẾT THÚC BATCH FETCH ---

        // 6. Ánh xạ (Map) trong bộ nhớ (cực nhanh)
        List<PostResponse> visiblePosts = posts.stream()
                .map(post -> {
                    PostResponse dto = postMapper.toDto(post);

                    // Gán React
                    dto.setReactSummary(reactMap.getOrDefault(
                            post.getId(),
                            ReactSummaryDto.builder()
                                    .counts(Collections.emptyMap())
                                    .total(0L)
                                    .currentUserReact(null)
                                    .build()
                            )
                    );

                    // Gán Share count
                    dto.setShareCount(shareCountMap.getOrDefault(post.getId(), 0));

                    // Gán Shared Post DTO (nếu xem được)
                    if (post.getSharedPost() != null) {
                        dto.setSharedPost(
                                visibleSharedPostDtoMap.get(post.getSharedPost().getId()) // Trả về DTO hoặc null
                        );
                    }

                    return dto;
                })
                .toList();

        // Trả về PageVO
        return PageVO.<PostResponse>builder()
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .numberOfElements(visiblePosts.size())
                .content(visiblePosts)
                .build();
    }

    @Transactional(readOnly = true)
    protected PostResponse toDtoWithReactsAndShares(Post post, Long viewerId) {
        PostResponse dto = postMapper.toDto(post);

        // 1. Gán Reacts (Query 1)
        dto.setReactSummary(reactService.getReactSummary(post.getId(), TargetType.POST, viewerId));

        // 2. Gán Share Count (Query 2)
        dto.setShareCount(postRepository.countSharesByPostId(post.getId()));

        // 3. Gán Shared Post (Query 3 - nếu có)
        if (post.getSharedPost() != null) {
            Post sharedPost = post.getSharedPost();
            if (canViewPost(userRepository.findById(viewerId).get(), sharedPost)) {
                // Đệ quy: Lấy DTO của bài share (cũng có react/share)
                dto.setSharedPost(toDtoWithReactsAndShares(sharedPost, viewerId));
            } else {
                dto.setSharedPost(null); // Không có quyền xem
            }
        }

        return dto;
    }

    private void checkViewPermission(User viewer, Post post) {
        User author = post.getAuthor();
        if (viewer.getId().equals(author.getId())) {
            return; // Tác giả luôn xem được
        }

        if (canView(viewer.getCredential().getRoles())) {
            return;
        }

        switch (post.getAccessModifier()) {
            case PRIVATE:
                throw new AccessDeniedException("You don't have permission to view this private post");

            case FRIENDS:
                if (!friendshipRepository.existsActiveFriendship(author, viewer)) {
                    throw new AccessDeniedException("Only friends can view this post");
                }
                break; // Là bạn, được xem

            case PUBLIC:
                break; // Ai cũng được xem
        }
    }

    private boolean canViewPost(User viewer, Post post) {
        if (post == null) return false;
        User author = post.getAuthor();
        if (viewer.getId().equals(author.getId())) return true; // Tác giả luôn xem được

        return switch (post.getAccessModifier()) {
            case PUBLIC -> true;
            case FRIENDS -> friendshipRepository.existsActiveFriendship(author, viewer);
            case PRIVATE -> false;
        };
    }

    private boolean isVideo(String ext) {
        return List.of("mp4", "webm", "ogg", "mov", "quicktime").contains(ext.toLowerCase());
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf(".");
        return dot != -1 ? filename.substring(dot + 1).toLowerCase() : "";
    }

    private String getTypeFromUrl(String url) {
        String ext = getExtension(url);
        return isVideo(ext) ? "video" : "image";
    }


    private boolean canView(Set<Role> roles) {
        if (roles == null || roles.isEmpty()) return false;

        return roles.stream()
                .filter(Objects::nonNull)
                .map(Role::getName)
                .filter(Objects::nonNull)
                .map(String::toUpperCase)
                .anyMatch(n -> n.equals("ADMIN") || n.equals("MODERATOR"));
    }

}