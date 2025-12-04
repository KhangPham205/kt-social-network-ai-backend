package com.kt.social.domain.recommendation.service;

import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.post.dto.PostResponse;

public interface PostRecommendationService {
    PageVO<PostResponse> getExploreFeed(int page, int size);
}
