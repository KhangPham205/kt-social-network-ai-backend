package com.kt.social.domain.post.service.impl;

import com.kt.social.auth.repository.UserCredentialRepository;
import com.kt.social.auth.util.SecurityUtils;
import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.friendship.enums.FriendshipStatus;
import com.kt.social.domain.friendship.repository.FriendshipRepository;
import com.kt.social.domain.post.dto.PostRequest;
import com.kt.social.domain.post.dto.PostResponse;
import com.kt.social.domain.post.enums.AccessScope;
import com.kt.social.domain.post.mapper.PostMapper;
import com.kt.social.domain.post.model.Post;
import com.kt.social.domain.post.repository.PostRepository;
import com.kt.social.domain.post.service.PostService;
import com.kt.social.domain.user.model.User;
import com.kt.social.domain.user.repository.UserRepository;
import com.kt.social.infra.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    private final UserCredentialRepository credRepo;
    private final FriendshipRepository friendshipRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final PostMapper postMapper;

    @Override
    @Transactional
    public PostResponse create(PostRequest request) {
        User author = SecurityUtils.getCurrentUser(credRepo, userRepository);

        String mediaUrl = Optional.ofNullable(request.getMedia())
                .filter(file -> !file.isEmpty())
                .map(file -> storageService.saveFile(file, "posts"))
                .orElse(null);

        Post sharedPost = Optional.ofNullable(request.getSharedPostId())
                .flatMap(postRepository::findById)
                .orElse(null);

        Post post = Post.builder()
                .author(author)
                .content(request.getContent())
                .mediaUrl(mediaUrl)
                .accessModifier(request.getAccessModifier())
                .sharedPost(sharedPost)
                .build();

        postRepository.save(post);
        return postMapper.toDto(post);
    }

    @Override
    @Transactional(readOnly = true)
    public PageVO<PostResponse> getUserPosts(Long userId, Pageable pageable) {
        User viewer = SecurityUtils.getCurrentUser(credRepo, userRepository);
        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Page<Post> page = postRepository.findByAuthor(targetUser, pageable);

        List<PostResponse> visiblePosts = page.stream()
                .map(post -> toResponseWithAccessCheck(viewer, post))
                .filter(Objects::nonNull) // lọc bỏ bài không xem được
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

    @Override
    @Transactional(readOnly = true)
    public PageVO<PostResponse> getMyPosts(Pageable pageable) {
        User current = SecurityUtils.getCurrentUser(credRepo, userRepository);
        Page<Post> page = postRepository.findByAuthor(current, pageable);

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

    @Override
    @Transactional
    public PostResponse sharePost(Long originalPostId, String caption) {
        User currentUser = SecurityUtils.getCurrentUser(credRepo, userRepository);
        Post original = postRepository.findById(originalPostId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        // Kiểm tra quyền share
        switch (original.getAccessModifier()) {
            case PRIVATE -> throw new RuntimeException("This post is private and cannot be shared.");
            case FRIENDS -> {
                boolean areFriends = friendshipRepository.existsByUserAndFriendAndStatusApproved(
                        currentUser, original.getAuthor()
                ) || friendshipRepository.existsByUserAndFriendAndStatusApproved(
                        original.getAuthor(), currentUser
                );
                if (!areFriends) {
                    throw new RuntimeException("You must be friends with the author to share this post.");
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
    @Transactional
    public void deletePost(Long postId) {
        Post original = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        List<Post> shares = postRepository.findBySharedPost(original);
        shares.forEach(shared -> shared.setSharedPost(null));
        postRepository.saveAll(shares);

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
            case FRIENDS -> friendshipRepository.existsByUserAndFriendAndStatus(viewer, original.getAuthor(), FriendshipStatus.ACCEPTED)
                    || friendshipRepository.existsByUserAndFriendAndStatus(original.getAuthor(), viewer, FriendshipStatus.ACCEPTED);
            default -> false;
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
        return dto;
    }
}