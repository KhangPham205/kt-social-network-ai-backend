package com.kt.social.auth.service;

import com.kt.social.auth.dto.LoginRequest;
import com.kt.social.auth.dto.RegisterRequest;
import com.kt.social.auth.dto.TokenResponse;

public interface AuthService {
    String register(RegisterRequest request);
    TokenResponse login(LoginRequest request);
    String sendVerificationCode(String email);
    boolean verifyEmail(String email, String code);
}