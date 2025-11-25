package com.kt.social.domain.recommendation.service.impl;

import com.kt.social.domain.friendship.dto.FriendRecommendationDto;
import com.kt.social.domain.recommendation.service.RecommendationService;
import com.kt.social.domain.user.service.UserService;
import com.kt.social.infra.neo4j.graph.repository.UserGraphRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecommendationServiceImpl implements RecommendationService {

    private final UserGraphRepository userGraphRepository;
    private final UserService userService;

    @Override
    @Transactional(readOnly = true, transactionManager = "neo4jTransactionManager")
    public List<FriendRecommendationDto> getRecommendations(int limit) {
        Long currentUserId = userService.getCurrentUser().getId();

        // 1. Gọi Neo4j (Lấy về List DTO)
        List<UserGraphRepository.RecommendationResultDto> results =
                userGraphRepository.findRecommendations(currentUserId, limit);

        // 2. Map kết quả
        return results.stream().map(res -> {
            String reason;
            double score;

            if (res.getDepth() == 2) {
                // Cấp 2: Bạn chung
                reason = res.getMutualCount() + " bạn chung";
                score = 100 + (res.getMutualCount() * 10); // Ưu tiên cao
            } else {
                // Cấp >3: Người quen xa
                reason = "Có thể bạn biết qua người quen";
                score = 50; // Ưu tiên thấp hơn
            }

            return FriendRecommendationDto.builder()
                    .userId(res.getUserId())
                    .displayName(res.getDisplayName())
                    .avatarUrl(res.getAvatarUrl())
                    .mutualFriendsCount(res.getMutualCount().intValue())
                    .totalScore(score)
                    .reason(reason)
                    .build();
        }).collect(Collectors.toList());
    }
}