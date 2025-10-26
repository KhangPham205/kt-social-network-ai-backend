package com.kt.social.domain.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDto {
    private Long id;
    private String displayName;
    private String avatarUrl;
    private Boolean isActive;

    private String bio;
    private String favorites;
    private Instant dateOfBirth;
}

