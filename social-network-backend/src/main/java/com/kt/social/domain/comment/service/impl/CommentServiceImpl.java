package com.kt.social.domain.comment.service.impl;

import com.kt.social.auth.repository.UserCredentialRepository;
import com.kt.social.auth.util.SecurityUtils;
import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.comment.dto.CommentRequest;
import com.kt.social.domain.comment.dto.CommentResponse;
import com.kt.social.domain.comment.dto.UpdateCommentRequest;
import com.kt.social.domain.comment.mapper.CommentMapper;
import com.kt.social.domain.comment.model.Comment;
import com.kt.social.domain.comment.repository.CommentRepository;
import com.kt.social.domain.comment.service.CommentService;
import com.kt.social.domain.post.model.Post;
import com.kt.social.domain.post.repository.PostRepository;
import com.kt.social.domain.user.model.User;
import com.kt.social.domain.user.repository.UserRepository;
import com.kt.social.infra.storage.service.StorageService;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final UserCredentialRepository credRepo;
    private final CommentMapper commentMapper;
    private final StorageService storageService;

    // ---------------- CREATE ----------------
    @Override
    @Transactional
    public CommentResponse createComment(CommentRequest request) {
        User author = SecurityUtils.getCurrentUser(credRepo, userRepository);
        Post post = postRepository.findById(request.getPostId())
                .orElseThrow(() -> new RuntimeException("Post not found"));

        Comment parent = null;
        if (request.getParentId() != null) {
            parent = commentRepository.findById(request.getParentId())
                    .orElseThrow(() -> new RuntimeException("Parent comment not found"));
        }

        String mediaUrl = null;
        if (request.getMediaFile() != null && !request.getMediaFile().isEmpty()) {
            mediaUrl = storageService.saveFile(request.getMediaFile(), "comments");
        }

        Comment comment = Comment.builder()
                .content(request.getContent())
                .mediaUrl(mediaUrl)
                .post(post)
                .author(author)
                .parent(parent)
                .createdAt(Instant.now())
                .build();

        Comment saved = commentRepository.save(comment);
        safeUpdateCommentCount(post.getId(), 1);
        return commentMapper.toDto(saved);
    }

    // ---------------- UPDATE ----------------
    @Override
    @Transactional
    public CommentResponse updateComment(UpdateCommentRequest request) {
        User current = SecurityUtils.getCurrentUser(credRepo, userRepository);

        Comment comment = commentRepository.findById(request.getCommentId())
                .orElseThrow(() -> new RuntimeException("Comment not found"));

        if (!comment.getAuthor().getId().equals(current.getId())) {
            throw new RuntimeException("You can only edit your own comment");
        }

        if (request.getContent() != null) comment.setContent(request.getContent());

        if (Boolean.TRUE.equals(request.getRemoveImage()) && comment.getMediaUrl() != null) {
            storageService.deleteFile(comment.getMediaUrl());
            comment.setMediaUrl(null);
        }

        if (request.getImageFile() != null && !request.getImageFile().isEmpty()) {
            if (comment.getMediaUrl() != null) storageService.deleteFile(comment.getMediaUrl());
            String newImageUrl = storageService.saveFile(request.getImageFile(), "comments");
            comment.setMediaUrl(newImageUrl);
        }

        commentRepository.save(comment);
        return commentMapper.toDto(comment);
    }

    // ---------------- GET COMMENT ROOT (depth 0) ----------------
    @Override
    @Transactional(readOnly = true)
    public PageVO<CommentResponse> getCommentsByPost(Long postId, Pageable pageable) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        Page<Comment> rootComments = commentRepository.findByPostAndParentIsNull(post, pageable);

        List<CommentResponse> content = rootComments.stream()
                .map(comment -> {
                    CommentResponse dto = commentMapper.toDto(comment);
                    dto.setDepth(0);
                    dto.setParentId(null);
                    dto.setChildrenCount(commentRepository.countByParent(comment)); // số phản hồi cấp 1
                    return dto;
                })
                .toList();

        return PageVO.<CommentResponse>builder()
                .page(rootComments.getNumber())
                .size(rootComments.getSize())
                .totalElements(rootComments.getTotalElements())
                .totalPages(rootComments.getTotalPages())
                .numberOfElements(content.size())
                .content(content)
                .build();
    }

    // ---------------- GET REPLIES ----------------
    @Override
    @Transactional(readOnly = true)
    public PageVO<CommentResponse> getReplies(Long parentId, Pageable pageable) {
        Comment parent = commentRepository.findById(parentId)
                .orElseThrow(() -> new RuntimeException("Parent comment not found"));

        Page<Comment> replies = commentRepository.findByParent(parent, pageable);

        List<CommentResponse> content = replies.stream()
                .map(reply -> {
                    CommentResponse dto = commentMapper.toDto(reply);
                    dto.setParentId(parent.getId());

                    // depth phụ thuộc vào tầng cha
                    int depth = parent.getParent() == null ? 1 : 2;
                    dto.setDepth(depth);

                    // nếu là tầng 1 → có thể còn children
                    if (depth == 1) {
                        dto.setChildrenCount(commentRepository.countByParent(reply));
                    }
                    // nếu là tầng 2 → không đệ quy, FE hiển thị phẳng
                    return dto;
                })
                .collect(Collectors.toList());

        return PageVO.<CommentResponse>builder()
                .page(replies.getNumber())
                .size(replies.getSize())
                .totalElements(replies.getTotalElements())
                .totalPages(replies.getTotalPages())
                .numberOfElements(content.size())
                .content(content)
                .build();
    }

    // ---------------- DELETE ----------------
    @Override
    @Transactional
    public void deleteComment(Long id) {
        Comment comment = commentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Comment not found"));
        User current = SecurityUtils.getCurrentUser(credRepo, userRepository);
        if (!comment.getAuthor().getId().equals(current.getId())) {
            throw new RuntimeException("You can only delete your own comment");
        }
        commentRepository.delete(comment);
        safeUpdateCommentCount(comment.getPost().getId(), -1);
    }

    // ---------------- SAFE UPDATE COMMENT COUNT WITH RETRY ----------------
    @Transactional
    public void safeUpdateCommentCount(Long postId, int delta) {
        for (int i = 0; i < 3; i++) {
            try {
                postRepository.updateCommentCount(postId, delta);
                return;
            } catch (OptimisticLockException e) {
                if (i == 2) throw e; // thử tối đa 3 lần
            }
        }
    }
}