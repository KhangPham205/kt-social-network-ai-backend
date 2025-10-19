package com.kt.social.auth.service.impl;

import com.kt.social.auth.dto.PasswordResetRequest;
import com.kt.social.auth.dto.VerifyResetCodeRequest;
import com.kt.social.auth.model.PasswordResetToken;
import com.kt.social.auth.model.UserCredential;
import com.kt.social.auth.repository.PasswordResetTokenRepository;
import com.kt.social.auth.repository.UserCredentialRepository;
import com.kt.social.auth.service.PasswordResetService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class PasswordResetServiceImpl implements PasswordResetService {

    private final UserCredentialRepository userCredentialRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;

    private static final long EXPIRATION_TIME = 2 * 60; // 2 minutes

    @Override
    public String sendResetCode(PasswordResetRequest request) {
        Optional<UserCredential> userOpt = userCredentialRepository.findByEmail(request.getEmail());
        if (userOpt.isEmpty()) {
            throw new RuntimeException("No account found with this email");
        }

        // Xóa mã cũ nếu tồn tại
        passwordResetTokenRepository.deleteByEmail(request.getEmail());

        String code = String.format("%06d", new Random().nextInt(999999)); // ví dụ 6 chữ số OTP

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .email(request.getEmail())
                .code(code)
                .newPassword(passwordEncoder.encode(request.getNewPassword()))
                .expiryDate(Instant.now().plusSeconds(EXPIRATION_TIME))
                .build();

        passwordResetTokenRepository.save(resetToken);

        // Giả lập gửi email — tạm thời trả về code để debug
        return code;
    }

    @Override
    public void verifyCodeAndResetPassword(VerifyResetCodeRequest request) {
        PasswordResetToken token = passwordResetTokenRepository.findByEmailAndCode(request.getEmail(), request.getCode())
                .orElseThrow(() -> new RuntimeException("Invalid or expired code"));

        if (token.getExpiryDate().isBefore(Instant.now())) {
            throw new RuntimeException("Code expired");
        }

        UserCredential user = userCredentialRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setPassword(token.getNewPassword());
        userCredentialRepository.save(user);

        passwordResetTokenRepository.delete(token);
    }
}
