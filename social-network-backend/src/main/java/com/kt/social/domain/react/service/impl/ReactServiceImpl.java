package com.kt.social.domain.react.service.impl;

import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.comment.model.Comment;
import com.kt.social.domain.comment.repository.CommentRepository;
import com.kt.social.domain.post.model.Post;
import com.kt.social.domain.post.repository.PostRepository;
import com.kt.social.domain.react.dto.*;
import com.kt.social.domain.react.enums.TargetType;
import com.kt.social.domain.react.model.*;
import com.kt.social.domain.react.repository.*;
import com.kt.social.domain.react.service.ReactService;
import com.kt.social.domain.user.model.User;
import com.kt.social.domain.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

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
    public ReactResponse toggleReact(User user, ReactRequest req) {
        TargetType tType = req.getTargetType();
        Long targetId = req.getTargetId();

        // Kiểm tra nhanh sự tồn tại target (tránh N+1 query)
        boolean exists = switch (tType) {
            case POST -> postRepository.existsById(targetId);
            case COMMENT -> commentRepository.existsById(targetId);
            default -> true;
        };
        if (!exists) throw new RuntimeException("Target not found");

        // Kiểm tra react hiện tại
        var existing = reactRepository.findByUserAndTargetIdAndTargetType(user, targetId, tType).orElse(null);

        if (existing != null) {
            // Cùng loại → Hủy
            if (Objects.equals(existing.getReactType().getId(), req.getReactTypeId())) {
                reactRepository.delete(existing);
            } else {
                // Khác loại → đổi loại
                ReactType newType = reactTypeRepository.getReferenceById(req.getReactTypeId());
                existing.setReactType(newType);
                existing.setCreatedAt(Instant.now());
                reactRepository.save(existing);
            }
        } else {
            // Chưa có → thêm mới
            ReactType rt = reactTypeRepository.getReferenceById(req.getReactTypeId());
            reactRepository.save(
                    React.builder()
                            .user(user)
                            .targetId(targetId)
                            .targetType(tType)
                            .reactType(rt)
                            .createdAt(Instant.now())
                            .build()
            );
        }

        // Cập nhật đếm (sử dụng countBy để không cần fetch all)
        long count = reactRepository.countByTargetIdAndTargetType(targetId, tType);
        updateTargetReactCount(tType, targetId, count);

        return ReactResponse.builder()
                .targetId(targetId)
                .targetType(tType)
                .reactCount(count)
                .message("React updated")
                .build();
    }

    private void updateTargetReactCount(TargetType tType, Long targetId, long count) {
        switch (tType) {
            case POST -> postRepository.findById(targetId).ifPresent(p -> {
                p.setReactCount((int) count);
                postRepository.save(p);
            });
            case COMMENT -> commentRepository.findById(targetId).ifPresent(c -> {
                c.setReactCount((int) count);
                commentRepository.save(c);
            });
        }
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

    @Override
    public ReactSummaryDto getReactSummary(Long targetId, TargetType targetType, Long userId) {
        // 1. Lấy counts (Query 1 - giữ nguyên logic của bạn)
        List<Object[]> summary = reactRepository.summarizeByTarget(targetId, targetType);

        Map<String, Long> counts = summary.stream()
                .collect(Collectors.toMap(
                        arr -> (String) arr[0],      // reactType.name
                        arr -> (Long) arr[1]         // COUNT(*)
                ));

        // 2. 🔥 SỬA LẠI: Lấy react của user (Query 2 - Dùng query mới, hiệu quả hơn)
        String currentUserReact = reactRepository
                .findViewerReactNameForTarget(userId, targetId, targetType)
                .orElse(null);

        return ReactSummaryDto.builder()
                .counts(counts)
                .total(counts.values().stream().mapToLong(Long::longValue).sum())
                .currentUserReact(currentUserReact)
                .build();
    }

    @Override
    public Map<Long, ReactSummaryDto> getReactSummaries(List<Long> targetIds, Long viewerId, TargetType targetType) {
        if (targetIds == null || targetIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Map<String, Long>> allCountsMap = new HashMap<>();
        List<Object[]> summaryResults = reactRepository.summarizeByTargetList(targetIds, targetType);

        for (Object[] row : summaryResults) {
            Long targetId = (Long) row[0];
            String reactName = (String) row[1];
            Long count = (Long) row[2];

            allCountsMap.computeIfAbsent(targetId, k -> new HashMap<>()).put(reactName, count);
        }

        Map<Long, String> viewerReactsMap = reactRepository
                .findViewerReactsForTargetList(viewerId, targetIds, targetType)
                .stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],  // targetId
                        row -> (String) row[1] // reactName
                ));

        Map<Long, ReactSummaryDto> resultMap = new HashMap<>();
        for (Long targetId : targetIds) {
            Map<String, Long> counts = allCountsMap.getOrDefault(targetId, Map.of());
            long total = counts.values().stream().mapToLong(Long::longValue).sum();
            String currentUserReact = viewerReactsMap.get(targetId); // Sẽ là null nếu không react

            resultMap.put(targetId, new ReactSummaryDto(
                    counts,
                    total,
                    currentUserReact
            ));
        }

        return resultMap;
    }
}