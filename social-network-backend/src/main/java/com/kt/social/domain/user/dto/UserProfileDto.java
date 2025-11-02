package com.kt.social.domain.user.dto;

import jakarta.persistence.MappedSuperclass;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Data
@SuperBuilder
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

