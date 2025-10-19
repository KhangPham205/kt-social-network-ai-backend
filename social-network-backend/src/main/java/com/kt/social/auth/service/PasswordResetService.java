package com.kt.social.auth.service;

import com.kt.social.auth.dto.PasswordResetRequest;
import com.kt.social.auth.dto.VerifyResetCodeRequest;

public interface PasswordResetService {
    String sendResetCode(PasswordResetRequest request);
    void verifyCodeAndResetPassword(VerifyResetCodeRequest request);
}
