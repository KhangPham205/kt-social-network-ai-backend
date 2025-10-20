package com.kt.social.auth.service;

import com.kt.social.auth.dto.*;

public interface AuthService {
    String register(RegisterRequest request);
    TokenResponse login(LoginRequest request);
    SendVerifyEmailResponse sendVerificationCode(String email);
    boolean verifyEmail(VerifyEmailRequest request);
}