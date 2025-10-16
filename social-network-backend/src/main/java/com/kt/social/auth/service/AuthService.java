package com.kt.social.auth.service;

import com.kt.social.auth.dto.LoginRequest;
import com.kt.social.auth.dto.RegisterRequest;
import com.kt.social.auth.dto.TokenResponse;

public interface AuthService {
    TokenResponse register(RegisterRequest request);
    TokenResponse login(LoginRequest request);
}