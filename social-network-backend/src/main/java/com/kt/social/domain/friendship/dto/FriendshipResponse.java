package com.kt.social.domain.friendship.dto;

import com.kt.social.domain.friendship.enums.FriendshipStatus;
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
}
