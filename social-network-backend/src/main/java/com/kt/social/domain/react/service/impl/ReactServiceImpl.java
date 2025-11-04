package com.kt.social.domain.react.service.impl;

import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.comment.model.Comment;
import com.kt.social.domain.comment.repository.CommentRepository;
import com.kt.social.domain.post.model.Post;
import com.kt.social.domain.post.repository.PostRepository;
import com.kt.social.domain.react.dto.ReactRequest;
import com.kt.social.domain.react.dto.ReactResponse;
import com.kt.social.domain.react.dto.ReactUserDto;
import com.kt.social.domain.react.enums.TargetType;
import com.kt.social.domain.react.model.React;
import com.kt.social.domain.react.model.ReactType;
import com.kt.social.domain.react.repository.ReactRepository;
import com.kt.social.domain.react.repository.ReactTypeRepository;
import com.kt.social.domain.react.service.ReactService;
import com.kt.social.domain.user.model.User;
import com.kt.social.domain.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ReactServiceImpl implements ReactService {

    private final ReactRepository reactRepository;
    private final ReactTypeRepository reactTypeRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

    @Override
    @Transactional
    public ReactResponse toggleReact(Long userId, ReactRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        TargetType tType = req.getTargetType();
        Long targetId = req.getTargetId();

        // Kiểm tra target có tồn tại
        if (tType == TargetType.POST) {
            postRepository.findById(targetId)
                    .orElseThrow(() -> new RuntimeException("Post not found"));
        } else if (tType == TargetType.COMMENT) {
            commentRepository.findById(targetId)
                    .orElseThrow(() -> new RuntimeException("Comment not found"));
        }

        // Kiểm tra react hiện tại của user
        var existingOpt = reactRepository.findByUserAndTargetIdAndTargetType(user, targetId, tType);

        if (existingOpt.isPresent()) {
            React existing = existingOpt.get();

            // Nếu react cùng loại → HỦY
            if (existing.getReactType().getId().equals(req.getReactTypeId())) {
                reactRepository.delete(existing);
            }
            // Nếu khác loại → cập nhật
            else {
                ReactType newType = reactTypeRepository.findById(req.getReactTypeId())
                        .orElseThrow(() -> new RuntimeException("ReactType not found"));
                existing.setReactType(newType);
                reactRepository.save(existing);
            }
        } else {
            // Nếu chưa react → thêm mới
            ReactType rt = reactTypeRepository.findById(req.getReactTypeId())
                    .orElseThrow(() -> new RuntimeException("ReactType not found"));
            React newReact = React.builder()
                    .user(user)
                    .targetId(targetId)
                    .targetType(tType)
                    .reactType(rt)
                    .createdAt(Instant.now())
                    .build();
            reactRepository.save(newReact);
        }

        // Cập nhật reactCount cho đúng đối tượng
        long count = reactRepository.countByTargetIdAndTargetType(targetId, tType);

        if (tType == TargetType.POST) {
            Post post = postRepository.findById(targetId).orElseThrow();
            post.setReactCount((int) count);
            postRepository.save(post);
        } else if (tType == TargetType.COMMENT) {
            Comment comment = commentRepository.findById(targetId).orElseThrow();
            comment.setReactCount((int) count);
            commentRepository.save(comment);
        }

        return ReactResponse.builder()
                .targetId(targetId)
                .targetType(tType)
                .reactCount(count)
                .message("React updated")
                .build();
    }

    @Override
    public long countReacts(Long targetId, TargetType targetType) {
        return reactRepository.countByTargetIdAndTargetType(targetId, targetType);
    }

    @Override
    @Transactional
    public PageVO<ReactUserDto> getReactUsers(Long targetId, TargetType targetType, Pageable pageable) {
        var page = reactRepository.findByTargetIdAndTargetType(targetId, targetType, pageable);

        var content = page.stream()
                .map(r -> ReactUserDto.builder()
                        .userId(r.getUser().getId())
                        .displayName(r.getUser().getDisplayName())
                        .avatarUrl(r.getUser().getAvatarUrl())
                        .reactTypeId(r.getReactType().getId())
                        .reactTypeName(r.getReactType().getName())
                        .build())
                .toList();

        return PageVO.<ReactUserDto>builder()
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .numberOfElements(content.size())
                .content(content)
                .build();
    }
}