package com.kt.social.domain.friendship.dto;

import com.kt.social.domain.friendship.enums.FriendshipStatus;
import com.kt.social.domain.friendship.model.Friendship;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FriendshipResponse {
    private String message;
    private FriendshipStatus status;
    private Long senderId;
    private Long receiverId;

    public static FriendshipResponse from(Friendship f, Long viewerId) {
        return FriendshipResponse.builder()
                .status(f.getStatus())
                // Logic này đảm bảo sender/receiver luôn nhất quán
                .senderId(f.getSender().getId().equals(viewerId) ? viewerId : f.getReceiver().getId())
                .receiverId(f.getSender().getId().equals(viewerId) ? f.getReceiver().getId() : viewerId)
                .build();
    }
}
