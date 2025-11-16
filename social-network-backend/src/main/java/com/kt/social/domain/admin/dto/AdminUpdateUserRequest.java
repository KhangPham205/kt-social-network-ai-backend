package com.kt.social.domain.admin.dto;

import com.kt.social.auth.enums.AccountStatus;
import lombok.Data;
import java.util.Set;

@Data
public class AdminUpdateUserRequest {
    private String displayName;
    private String bio;
    private AccountStatus status;
    private Set<String> roles; // (Danh sách tên Role mới, ví dụ: ["USER", "MODERATOR"])
}