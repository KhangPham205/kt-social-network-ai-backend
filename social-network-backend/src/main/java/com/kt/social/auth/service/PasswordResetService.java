package com.kt.social.auth.service;

import com.kt.social.auth.dto.PasswordResetRequest;

public interface PasswordResetService {
    void sendResetCode(PasswordResetRequest request);
}
