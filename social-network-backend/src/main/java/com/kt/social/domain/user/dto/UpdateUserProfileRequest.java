package com.kt.social.domain.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateUserProfileRequest {
    private String displayName;
    private String avatarUrl;
    private String bio;
    private String favorites;
    private Instant dateOfBirth;
}
