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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
        return commentMapper.toDto(saved);
    }

    @Override
    @Transactional
    public CommentResponse updateComment(UpdateCommentRequest request) {
        User current = SecurityUtils.getCurrentUser(credRepo, userRepository);

        Comment comment = commentRepository.findById(request.getCommentId())
                .orElseThrow(() -> new RuntimeException("Comment not found"));

        // Chỉ tác giả được phép chỉnh sửa
        if (!comment.getAuthor().getId().equals(current.getId())) {
            throw new RuntimeException("You can only edit your own comment");
        }

        // Cập nhật nội dung
        if (request.getContent() != null) {
            comment.setContent(request.getContent());
        }

        // Nếu user yêu cầu xóa ảnh
        if (Boolean.TRUE.equals(request.getRemoveImage()) && comment.getMediaUrl() != null) {
            storageService.deleteFile(comment.getMediaUrl());
            comment.setMediaUrl(null);
        }

        // Nếu có ảnh mới
        if (request.getImageFile() != null && !request.getImageFile().isEmpty()) {
            if (comment.getMediaUrl() != null) {
                storageService.deleteFile(comment.getMediaUrl());
            }
            String newImageUrl = storageService.saveFile(request.getImageFile(), "comments");
            comment.setMediaUrl(newImageUrl);
        }

        commentRepository.save(comment);
        return commentMapper.toDto(comment);
    }

    @Override
    @Transactional(readOnly = true)
    public PageVO<CommentResponse> getCommentsByPost(Long postId, Pageable pageable) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        // Lấy tất cả comment của bài viết trong 1 query
        List<Comment> allComments = commentRepository.findByPost(post);

        // Gom các comment theo parentId
        Map<Long, List<Comment>> groupedByParent = allComments.stream()
                .filter(c -> c.getParent() != null)
                .collect(Collectors.groupingBy(c -> c.getParent().getId()));

        // Lấy các comment gốc (không có parent)
        List<Comment> rootComments = allComments.stream()
                .filter(c -> c.getParent() == null)
                .sorted(Comparator.comparing(Comment::getCreatedAt).reversed()) // sắp xếp mới nhất trước
                .collect(Collectors.toList());

        // Phân trang thủ công (vì Pageable không còn dùng trực tiếp)
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), rootComments.size());
        List<Comment> pagedRoots = rootComments.subList(start, end);

        // Build cây comment từ map (đệ quy nhưng không query thêm)
        List<CommentResponse> content = pagedRoots.stream()
                .map(c -> buildCommentTreeCached(c, groupedByParent, 0))
                .toList();

        return PageVO.<CommentResponse>builder()
                .page(pageable.getPageNumber())
                .size(pageable.getPageSize())
                .totalElements((long) rootComments.size())
                .totalPages((int) Math.ceil((double) rootComments.size() / pageable.getPageSize()))
                .numberOfElements(content.size())
                .content(content)
                .build();
    }

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
    }

    // ----------------- Helper method ---------------------
    /**
     * Xây dựng cây comment đệ quy từ map cache (chỉ dùng dữ liệu đã query).
     */
    private CommentResponse buildCommentTreeCached(Comment comment, Map<Long, List<Comment>> groupedByParent, int depth) {
        CommentResponse dto = commentMapper.toDto(comment);
        dto.setDepth(depth);
        List<Comment> replies = groupedByParent.get(comment.getId());
        if (replies != null && !replies.isEmpty()) {
            dto.setChildren(replies.stream()
                    .map(c -> buildCommentTreeCached(c, groupedByParent, depth + 1))
                    .toList());
        }

        return dto;
    }
}