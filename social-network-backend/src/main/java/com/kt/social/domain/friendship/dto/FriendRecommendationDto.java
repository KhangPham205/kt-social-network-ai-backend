package com.kt.social.domain.friendship.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FriendRecommendationDto {
    private Long userId;
    private String displayName;
    private String avatarUrl;
    private int mutualFriendsCount; // Số bạn chung
//    private double interestScore;   // Điểm sở thích (0.0 - 1.0)
    private double totalScore;      // Tổng điểm xếp hạng
    private String reason;          // Ví dụ: "Có 5 bạn chung", "Cùng thích Bóng đá"
}