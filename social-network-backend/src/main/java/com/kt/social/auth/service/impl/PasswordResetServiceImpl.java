package com.kt.social.auth.service.impl;

import com.kt.social.auth.dto.PasswordResetRequest;
import com.kt.social.auth.enums.OtpType;
import com.kt.social.auth.model.PasswordResetToken;
import com.kt.social.auth.model.UserCredential;
import com.kt.social.auth.repository.PasswordResetTokenRepository;
import com.kt.social.auth.repository.UserCredentialRepository;
import com.kt.social.auth.service.EmailService;
import com.kt.social.auth.service.PasswordResetService;
import com.kt.social.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PasswordResetServiceImpl implements PasswordResetService {

    private final UserCredentialRepository userCredentialRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    private static final long EXPIRATION_TIME = 2 * 60; // 2 minutes

    @Override
    public void sendResetCode(PasswordResetRequest request) {
        Optional<UserCredential> userOpt = userCredentialRepository.findByEmail(request.getEmail());
        if (userOpt.isEmpty()) {
            throw new ResourceNotFoundException("No account found with this email");
        }

        // Xóa mã cũ nếu tồn tại
        passwordResetTokenRepository.deleteByEmail(request.getEmail());

        String code = String.format("%06d", new SecureRandom().nextInt(999999)); // ví dụ 6 chữ số OTP

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .email(request.getEmail())
                .code(code)
                .newPassword(passwordEncoder.encode(request.getNewPassword()))
                .expiryDate(Instant.now().plusSeconds(EXPIRATION_TIME))
                .build();

        passwordResetTokenRepository.save(resetToken);

        // Gửi email chứa mã xác thực
        emailService.sendEmail(userOpt.get(), OtpType.RESET_PASSWORD, code);
    }
}
