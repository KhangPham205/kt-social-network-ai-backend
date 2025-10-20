package com.kt.social.auth.service;

import com.kt.social.auth.dto.PasswordResetRequest;

public interface PasswordResetService {
    String sendResetCode(PasswordResetRequest request);
}
