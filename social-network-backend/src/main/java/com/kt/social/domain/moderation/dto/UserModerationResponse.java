package com.kt.social.domain.moderation.dto;

import com.kt.social.auth.enums.AccountStatus;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UserModerationResponse {
    private Long userId;
    private String username;
    private String email;
    private String displayName;
    private String avatar;
    private AccountStatus status;
    private long violationCount;

    // Constructor khớp với thứ tự trong @Query
    public UserModerationResponse(Long userId,
                                  String username,
                                  String email,
                                  String displayName,
                                  String avatar,
                                  AccountStatus status,
                                  Long violationCount) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.displayName = displayName;
        this.avatar = avatar;
        this.status = status;
        this.violationCount = violationCount != null ? violationCount : 0L;
    }
}