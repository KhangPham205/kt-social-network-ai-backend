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
import com.kt.social.domain.post.dto.UpdatePostRequest;
import com.kt.social.domain.post.enums.AccessScope;
import com.kt.social.domain.post.mapper.PostMapper;
import com.kt.social.domain.post.model.Post;
import com.kt.social.domain.post.repository.PostRepository;
import com.kt.social.domain.post.service.PostService;
import com.kt.social.domain.react.enums.TargetType;
import com.kt.social.domain.react.service.ReactService;
import com.kt.social.domain.user.model.User;
import com.kt.social.domain.user.model.UserRela;
import com.kt.social.domain.user.repository.UserRelaRepository;
import com.kt.social.domain.user.repository.UserRepository;
import com.kt.social.domain.user.service.UserService;
import com.kt.social.infra.storage.StorageService;
import io.github.perplexhub.rsql.RSQLJPASupport;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.*;
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
    private final UserService userService;
    private final ReactService reactService;
    private final PostMapper postMapper;

    @Override
    @Transactional
    public PostResponse create(String content, String accessModifier, List<MultipartFile> mediaFiles) {
        User author = SecurityUtils.getCurrentUser(credRepo, userRepository);

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
                .build();

        postRepository.save(post);
        return postMapper.toDto(post);
    }

    @Override
    @Transactional
    public PostResponse update(UpdatePostRequest request) {

        User currentUser = SecurityUtils.getCurrentUser(credRepo, userRepository);
        Post post = postRepository.findById(request.getPostId())
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        if (!post.getAuthor().getId().equals(currentUser.getId())) {
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

        return toResponseWithAccessCheck(viewer, post);
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
    public PostResponse sharePost(Long originalPostId, String caption, AccessScope accessScope) {
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
                .accessModifier(accessScope)
                .build();

        postRepository.save(shared);
        return postMapper.toDto(shared);
    }

    @Override
    @Transactional(readOnly = true)
    public PageVO<PostResponse> getFeed(Pageable pageable, String filter) {
        User current = SecurityUtils.getCurrentUser(credRepo, userRepository);

        var friends = friendshipRepository.findAllAcceptedFriends(current);
        var friendAndSelfIds = Stream.concat(
                        friends.stream(),
                        Stream.of(current) // Thêm chính mình vào
                )
                .map(User::getId)
                .distinct()
                .toList();

        var followings = userRelaRepository.findByFollower(current)
                .stream()
                .map(UserRela::getFollowing)
                .toList();

        var authorIds = Stream.concat(
                        friendAndSelfIds.stream(),
                        followings.stream().map(User::getId)
                )
                .distinct()
                .toList();

        Specification<Post> baseSpec = (root, query, cb) -> {
            query.distinct(true);

            Predicate authorPredicate = root.get("author").get("id").in(authorIds);
            Predicate publicPosts = cb.equal(root.get("accessModifier"), AccessScope.PUBLIC);

            Predicate friendPosts = cb.and(
                    cb.equal(root.get("accessModifier"), AccessScope.FRIENDS),
                    root.get("author").get("id").in(friendAndSelfIds)
            );

            Predicate privatePosts = cb.and(
                    cb.equal(root.get("accessModifier"), AccessScope.PRIVATE),
                    cb.equal(root.get("author"), current)
            );

            Predicate accessPredicate = cb.or(publicPosts, friendPosts, privatePosts);
            return cb.and(authorPredicate, accessPredicate);
        };

        Specification<Post> finalSpec = baseSpec;
        if (filter != null && !filter.isBlank()) {
            finalSpec = finalSpec.and(RSQLJPASupport.toSpecification(filter));
        }

        Page<Post> page = postRepository.findAll(finalSpec, pageable);

        return getPostResponsePageVO(current, page);
    }

    @Override
    @Transactional
    public void deletePost(Long postId) {
        User currentUser = SecurityUtils.getCurrentUser(credRepo, userRepository);

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        if (!post.getAuthor().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You are not authorized to delete this post.");
        }

        // Hủy liên kết share
        List<Post> shares = postRepository.findBySharedPost(post);
        shares.forEach(shared -> shared.setSharedPost(null));
        postRepository.saveAll(shares);

        postRepository.delete(post);
    }

    // --------------------- Helper methods -------------------------

    public boolean canViewSharedPost(User viewer, Post original) {
        if (original == null) return false;
        if (viewer.getId().equals(original.getAuthor().getId())) return true;

        return switch (original.getAccessModifier()) {
            case PUBLIC -> true;
            case FRIENDS -> friendshipRepository.existsBySenderAndReceiverAndStatus(viewer, original.getAuthor(), FriendshipStatus.FRIEND)
                    || friendshipRepository.existsBySenderAndReceiverAndStatus(original.getAuthor(), viewer, FriendshipStatus.FRIEND);
            default -> false;
        };
    }

    private boolean canViewPost(User viewer, Post post) {
        if (post == null) return false;
        User author = post.getAuthor();
        if (viewer.getId().equals(author.getId())) return true; // Tác giả luôn xem được

        return switch (post.getAccessModifier()) {
            case PUBLIC -> true;
            case FRIENDS -> friendshipRepository.existsBySenderAndReceiverAndStatus(viewer, author, FriendshipStatus.FRIEND)
                    || friendshipRepository.existsBySenderAndReceiverAndStatus(author, viewer, FriendshipStatus.FRIEND);
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

        if (post.getSharedPost() != null) {
            Optional<Post> visible = getVisibleSharedPost(viewer, post.getSharedPost());
            dto.setSharedPost(visible.map(postMapper::toDto).orElse(null));
        } else {
            dto.setSharedPost(null);
        }

        Long currentUserId = viewer.getId();
        dto.setReactSummary(reactService.getReactSummary(post.getId(), TargetType.POST, currentUserId));

        int shareCount = postRepository.countSharesByPostId(post.getId());
        dto.setShareCount(shareCount);

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
}