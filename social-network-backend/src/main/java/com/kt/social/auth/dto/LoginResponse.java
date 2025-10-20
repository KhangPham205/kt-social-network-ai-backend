package com.kt.social.auth.dto;

import com.kt.social.auth.enums.AccountStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class LoginResponse {
    private String email;
    private AccountStatus status;
    private TokenResponse token;
}