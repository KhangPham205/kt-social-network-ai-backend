package com.kt.social.domain.user.dto;

import com.kt.social.domain.friendship.dto.FriendshipResponse;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class UserRelationDto extends UserProfileDto {
    private boolean isFollowing;   // mình đang follow họ
    private boolean isFollowedBy;  // họ follow lại mình
    private FriendshipResponse friendship;
}