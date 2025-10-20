package com.kt.social.auth.service;

import com.kt.social.auth.dto.*;

public interface AuthService {
    RegisterResponse register(RegisterRequest request);
    LoginResponse login(LoginRequest request);
    SendVerifyEmailResponse sendVerificationCode(String email);
    boolean verifyOtp(OtpVerificationRequest request);
}