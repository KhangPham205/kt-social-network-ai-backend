package com.kt.social.domain.recommendation.service;

import com.kt.social.domain.friendship.dto.FriendRecommendationDto;

import java.util.List;

public interface RecommendationService {

    List<FriendRecommendationDto> getRecommendations(int limit);

}
