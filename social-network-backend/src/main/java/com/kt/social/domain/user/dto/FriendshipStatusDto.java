package com.kt.social.domain.user.dto;

import com.kt.social.domain.friendship.enums.FriendshipStatus;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FriendshipStatusDto {
    private boolean exists;            // Có mối quan hệ không
    private FriendshipStatus status;   // ACCEPTED, PENDING, REJECTED, BLOCKED, ...
    private Long requesterId;          // Ai gửi lời mời
    private Long receiverId;           // Ai nhận
}