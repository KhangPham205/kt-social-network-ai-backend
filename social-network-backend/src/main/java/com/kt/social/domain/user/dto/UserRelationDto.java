package com.kt.social.domain.user.dto;

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
    private boolean isFriend;      // hai người là bạn bè
}