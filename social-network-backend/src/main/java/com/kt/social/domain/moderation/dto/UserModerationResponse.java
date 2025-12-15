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
    private boolean isLocked;
    private Long reportCount;

    // Constructor khớp với câu Query JPQL
    public UserModerationResponse(Long userId, String username, String email, String displayName, String avatar, AccountStatus status, Long reportCount) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.displayName = displayName;
        this.avatar = avatar;
        // Logic xác định user có bị khóa hay không dựa trên Enum AccountStatus
        this.isLocked = (status == AccountStatus.BLOCKED);
        this.reportCount = reportCount;
    }
}