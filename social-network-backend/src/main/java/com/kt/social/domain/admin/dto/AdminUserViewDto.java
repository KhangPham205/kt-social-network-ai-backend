package com.kt.social.domain.admin.dto;

import com.kt.social.auth.enums.AccountStatus;
import lombok.Data;
import java.time.Instant;
import java.util.Set;

@Data
public class AdminUserViewDto {
    // Từ User
    private Long id;
    private String displayName;
    private String avatarUrl;
//    private boolean isActive;

    // Từ UserCredential
    private Long credentialId;
    private String username;
    private String email;
    private AccountStatus status;
    private Set<String> roles; // (Tên các Role)

    // Từ UserInfo
    private String bio;
    private Instant dateOfBirth;
    private String favorites;
}
