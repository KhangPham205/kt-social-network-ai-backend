package com.kt.social.auth.dto;

import com.kt.social.auth.enums.AccountStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
@Builder
public class LoginResponse {
    private String email;
    private AccountStatus status;
    private List<String> roles;
    private TokenResponse token;
}