package com.kt.social.auth.service;

import com.kt.social.auth.enums.OtpType;
import com.kt.social.auth.model.UserCredential;
import jakarta.mail.MessagingException;

public interface EmailService {
    void sendVerificationEmail(String to, String subject, String body) throws MessagingException;
    void sendEmail(UserCredential user, OtpType otpType, String otp);
}
