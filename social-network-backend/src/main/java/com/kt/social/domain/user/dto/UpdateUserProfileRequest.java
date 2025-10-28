package com.kt.social.domain.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateUserProfileRequest {
    private String displayName;
    private MultipartFile avatarFile;
    private String bio;
    private String favorites;
    private Instant dateOfBirth;
}
