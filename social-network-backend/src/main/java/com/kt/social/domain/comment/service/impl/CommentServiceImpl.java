package com.kt.social.domain.comment.service.impl;

import com.kt.social.common.exception.AccessDeniedException;
import com.kt.social.common.exception.BadRequestException;
import com.kt.social.common.exception.ResourceNotFoundException;
import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.audit.service.ActivityLogService;
import com.kt.social.domain.comment.dto.CommentRequest;
import com.kt.social.domain.comment.dto.CommentResponse;
import com.kt.social.domain.comment.dto.UpdateCommentRequest;
import com.kt.social.domain.comment.mapper.CommentMapper;
import com.kt.social.domain.comment.model.Comment;
import com.kt.social.domain.comment.repository.CommentRepository;
import com.kt.social.domain.comment.service.CommentService;
import com.kt.social.domain.friendship.repository.FriendshipRepository;
import com.kt.social.domain.moderation.event.ContentCreatedEvent;
import com.kt.social.domain.notification.enums.NotificationType;
import com.kt.social.domain.notification.service.NotificationService;
import com.kt.social.domain.post.model.Post;
import com.kt.social.domain.post.repository.PostRepository;
import com.kt.social.domain.react.dto.ReactSummaryDto;
import com.kt.social.domain.react.enums.TargetType;
import com.kt.social.domain.react.service.ReactService;
import com.kt.social.domain.user.model.User;
import com.kt.social.domain.user.service.UserService;
import com.kt.social.infra.ai.AiServiceClient;
import com.kt.social.infra.storage.StorageService;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final ApplicationEventPublisher eventPublisher;
    private final FriendshipRepository friendshipRepository;
    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final CommentMapper commentMapper;
    private final UserService userService;
    private final ReactService reactService;
    private final StorageService storageService;
    private final NotificationService notificationService;
    private final ActivityLogService activityLogService;

    // ---------------- CREATE ----------------
    @Override
    @Transactional
    public CommentResponse createComment(CommentRequest request) {
        User author = userService.getCurrentUser();
        Post post = postRepository.findById(request.getPostId())
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        checkViewPermission(author, post);

        Comment parent = null;
        if (request.getParentId() != null) {
            parent = commentRepository.findById(request.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent comment not found"));
        }

        List<Map<String, String>> mediaList = List.of();
        if (request.getMediaFile() != null && !request.getMediaFile().isEmpty()) {
            String url = storageService.saveFile(request.getMediaFile(), "comments");
            String ext = getExtension(Objects.requireNonNull(request.getMediaFile().getOriginalFilename()));
            String type = isVideo(ext) ? "video" : "image";
            mediaList = List.of(Map.of("url", url, "type", type));
        }

        Comment comment = Comment.builder()
                .content(request.getContent())
                .media(mediaList)
                .post(post)
                .author(author)
                .parent(parent)
                .createdAt(Instant.now())
                .build();

        Comment saved = commentRepository.save(comment);

        eventPublisher.publishEvent(new ContentCreatedEvent(
                saved.getId(),
                TargetType.COMMENT,
                saved.getContent(),
                saved.getAuthor().getId()
        ));

        safeUpdateCommentCount(post.getId(), 1);

        if (request.getParentId() != null) {
            // Đây là Reply
            assert parent != null;
            User parentAuthor = parent.getAuthor();
            notificationService.sendNotification(
                    author,
                    parentAuthor,
                    NotificationType.REPLY_COMMENT,
                    saved.getId(), // ID của reply
                    post.getId()   // ID của post gốc
            );
        } else {
            // Đây là Comment gốc
            User postAuthor = post.getAuthor();
            notificationService.sendNotification(
                    author,
                    postAuthor,
                    NotificationType.COMMENT_POST,
                    saved.getId(), // ID của comment
                    post.getId()   // ID của post gốc
            );
        }

        String action = (parent == null) ? "COMMENT:CREATE" : "COMMENT:REPLY";
        activityLogService.logActivity(
                author,
                action,
                "Comment",
                saved.getId(),
                Map.of("postId", post.getId())
        );

        return toDtoWithChildrenAndReacts(saved, author.getId(), 0);
    }

    // ---------------- UPDATE ----------------
    @Override
    @Transactional
    public CommentResponse updateComment(UpdateCommentRequest request) {
        User current = userService.getCurrentUser();
        Comment comment = commentRepository.findById(request.getCommentId())
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));

        boolean isOwner = comment.getAuthor().getId().equals(current.getId());
        boolean canUpdate = currentUserHasAuthority("COMMENT:UPDATE");

        if (!(isOwner || canUpdate)) {
            throw new AccessDeniedException("You are not authorized to update this comment.");
        }

        // Cập nhật nội dung nếu có
        if (request.getContent() != null) {
            comment.setContent(request.getContent());
        }

        // Xóa media cũ nếu removeMedia = true
        if (Boolean.TRUE.equals(request.getRemoveMedia())) {
            if (comment.getMedia() != null && !comment.getMedia().isEmpty()) {
                comment.getMedia().forEach(m -> {
                    String url = m.get("url");
                    if (url != null && !url.isBlank()) {
                        storageService.deleteFile(url);
                    }
                });
            }
            comment.setMedia(List.of());
        }

        // Upload file mới (1 file duy nhất)
        if (request.getMediaFile() != null && !request.getMediaFile().isEmpty()) {
            // Nếu removeMedia = false, vẫn xóa media cũ trước khi thêm mới
            if (comment.getMedia() != null && !comment.getMedia().isEmpty()) {
                comment.getMedia().forEach(m -> {
                    String url = m.get("url");
                    if (url != null && !url.isBlank()) {
                        storageService.deleteFile(url);
                    }
                });
            }

            String url = storageService.saveFile(request.getMediaFile(), "comments");
            String ext = getExtension(Objects.requireNonNull(request.getMediaFile().getOriginalFilename()));
            String type = isVideo(ext) ? "video" : "image";

            comment.setMedia(List.of(Map.of("url", url, "type", type)));
        }

        Comment saved = commentRepository.save(comment);
        eventPublisher.publishEvent(new ContentCreatedEvent(
                saved.getId(),
                TargetType.COMMENT,
                saved.getContent(),
                saved.getAuthor().getId()
        ));

        activityLogService.logActivity(
                current,
                "COMMENT:UPDATE",
                "Comment",
                saved.getId(),
                Map.of("postId", comment.getPost().getId())
        );

        return toDtoWithChildrenAndReacts(saved, current.getId(), -1);
    }

    @Override
    public CommentResponse getCommentById(Long id) {
        Comment comment = commentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));

        User currentUser = userService.getCurrentUser();

        return toDtoWithChildrenAndReacts(comment, currentUser.getId(), -1);
    }

    // ---------------- GET COMMENT ROOT (depth 0) ----------------
    @Override
    @Transactional(readOnly = true)
    public PageVO<CommentResponse> getCommentsByPost(Long postId, Pageable pageable) {
        User currentUser = userService.getCurrentUser();

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        checkViewPermission(currentUser, post);

        Page<Comment> rootComments = commentRepository.findByPostAndParentIsNull(post, pageable);

        return buildPageVO(rootComments, currentUser.getId(), 0);
    }

    // ---------------- GET REPLIES ----------------
    @Override
    @Transactional(readOnly = true)
    public PageVO<CommentResponse> getReplies(Long parentId, Pageable pageable) {
        Comment parent = commentRepository.findById(parentId)
                .orElseThrow(() -> new ResourceNotFoundException("Parent comment not found"));

        User currentUser = userService.getCurrentUser();
        Page<Comment> replies = commentRepository.findByParent(parent, pageable);

        int depth = (parent.getParent() == null) ? 1 : 2;
        return buildPageVO(replies, currentUser.getId(), depth);
    }

    // ---------------- DELETE ----------------
    @Override
    @Transactional
    public void deleteComment(Long id) {
        Comment comment = commentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));

        User current = userService.getCurrentUser();

        boolean isOwner = comment.getAuthor().getId().equals(current.getId());
        boolean canDelete = currentUserHasAuthority("COMMENT:DELETE");
        boolean canDeleteAny = currentUserHasAuthority("COMMENT:DELETE_ANY");

        if ((isOwner && canDelete) || canDeleteAny) {

        } else {
            throw new AccessDeniedException("You are not authorized to delete this comment.");
        }

        commentRepository.delete(comment);
        safeUpdateCommentCount(comment.getPost().getId(), -1);

        activityLogService.logActivity(
                current,
                "COMMENT:DELETE",
                "Comment",
                id,
                Map.of("postId", comment.getPost().getId(),
                        "deletedCommentAuthorId", comment.getAuthor().getId())
        );
    }

    // ---------------- SAFE UPDATE COMMENT COUNT WITH RETRY ----------------
    @Transactional
    public void safeUpdateCommentCount(Long postId, int delta) {
        for (int i = 0; i < 3; i++) {
            try {
                postRepository.updateCommentCount(postId, delta);
                return;
            } catch (OptimisticLockException e) {
                if (i == 2) throw e;
            }
        }
    }

    // ---------------- PRIVATE HELPER METHODS ----------------

    private PageVO<CommentResponse> buildPageVO(Page<Comment> page, Long viewerId, int depth) {
        List<Comment> comments = page.getContent();
        if (comments.isEmpty()) {
            return PageVO.<CommentResponse>builder()
                    .page(page.getNumber())
                    .size(page.getSize())
                    .totalElements(page.getTotalElements())
                    .totalPages(page.getTotalPages())
                    .numberOfElements(0)
                    .content(List.of())
                    .build();
        }

        List<Long> commentIds = comments.stream().map(Comment::getId).toList();

        Map<Long, ReactSummaryDto> reactMap = reactService.getReactSummaries(commentIds, viewerId, TargetType.COMMENT);

        Map<Long, Integer> childrenCountMap = (depth < 2)
                ? commentRepository.findChildrenCounts(commentIds)
                : Map.of();

        List<CommentResponse> content = comments.stream()
                .map(comment -> {
                    CommentResponse dto = commentMapper.toDto(comment);
                    dto.setReactSummary(reactMap.getOrDefault(
                            comment.getId(),
                            ReactSummaryDto.builder()
                                    .counts(Collections.emptyMap())
                                    .total(0L)
                                    .currentUserReact(null)
                                    .build())
                    );
                    dto.setChildrenCount(childrenCountMap.getOrDefault(comment.getId(), 0));
                    dto.setDepth(depth);
                    return dto;
                })
                .toList();

        return PageVO.<CommentResponse>builder()
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .numberOfElements(content.size())
                .content(content)
                .build();
    }

    private CommentResponse toDtoWithChildrenAndReacts(Comment comment, Long viewerId, int depth) {
        CommentResponse dto = commentMapper.toDto(comment);

        dto.setReactSummary(reactService.getReactSummary(comment.getId(), TargetType.COMMENT, viewerId));

        dto.setChildrenCount(commentRepository.countByParent(comment));

        if (depth >= 0) {
            dto.setDepth(depth);
        }

        return dto;
    }

    private boolean currentUserHasAuthority(String authority) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(authority));
    }

    /**
     * HELPER 2: Kiểm tra quyền XEM POST (sao chép từ PostService)
     * Ném Exception nếu không có quyền
     */
    private void checkViewPermission(User viewer, Post post) {
        User author = post.getAuthor();

        boolean isOwner = viewer.getId().equals(author.getId());
        boolean canReadAny = currentUserHasAuthority("POST:READ_ANY"); // (Quyền Admin)

        if (isOwner || canReadAny) {
            return; // Chủ sở hữu hoặc Admin/Mod -> luôn xem được
        }

        switch (post.getAccessModifier()) {
            case PRIVATE:
                throw new AccessDeniedException("You don't have permission to view this private post");
            case FRIENDS:
                if (!friendshipRepository.existsActiveFriendship(author, viewer)) {
                    throw new AccessDeniedException("Only friends can view this post");
                }
                break;
            case PUBLIC:
                break;
        }
    }

    private boolean isVideo(String ext) {
        return List.of("mp4", "webm", "ogg", "mov", "quicktime").contains(ext.toLowerCase());
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf(".");
        return dot != -1 ? filename.substring(dot + 1).toLowerCase() : "";
    }
}