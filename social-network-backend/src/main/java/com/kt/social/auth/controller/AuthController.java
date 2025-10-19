package com.kt.social.auth.controller;

import com.kt.social.auth.dto.*;
import com.kt.social.auth.service.AuthService;
import com.kt.social.auth.service.PasswordResetService;
import com.kt.social.auth.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(refreshTokenService.refresh(request));
    }

    @PostMapping("/sendVerifyEmail")
    public ResponseEntity<?> sendVerifyEmail(@RequestParam String email) {
        String code = authService.sendVerificationCode(email);
        return ResponseEntity.ok(Map.of("message", "Verification code generated successfully", "code", code));
    }

    @PostMapping("/verifyEmail")
    public ResponseEntity<?> verifyEmail(@RequestParam String email, @RequestParam String code) {
        boolean success = authService.verifyEmail(email, code);
        if (success) {
            return ResponseEntity.ok(Map.of("message", "Email verified successfully"));
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid verification code"));
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@RequestBody PasswordResetRequest request) {
        String code = passwordResetService.sendResetCode(request);
        return ResponseEntity.ok(Map.of(
                "message", "Verification code sent to email (simulated)",
                "code", code
        ));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> verifyResetCode(@RequestBody VerifyResetCodeRequest request) {
        passwordResetService.verifyCodeAndResetPassword(request);
        return ResponseEntity.ok(Map.of("message", "Password has been successfully reset"));
    }
}