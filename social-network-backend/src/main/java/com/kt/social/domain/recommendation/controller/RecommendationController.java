package com.kt.social.domain.recommendation.controller;


import com.kt.social.common.constants.ApiConstants;
import com.kt.social.domain.friendship.dto.FriendRecommendationDto;
import com.kt.social.domain.recommendation.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(ApiConstants.RECOMMENDATIONS)
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    /**
     * GET /api/v1/recommendations/friends?limit=10
     * Lấy danh sách gợi ý kết bạn (Dựa trên thuật toán Neo4j)
     */
    @GetMapping("/friends")
    public ResponseEntity<List<FriendRecommendationDto>> getFriendRecommendations(
            @RequestParam(defaultValue = "10") int limit
    ) {
        // Service sẽ tự lấy currentUserId từ SecurityContext
        return ResponseEntity.ok(recommendationService.getRecommendations(limit));
    }
}
