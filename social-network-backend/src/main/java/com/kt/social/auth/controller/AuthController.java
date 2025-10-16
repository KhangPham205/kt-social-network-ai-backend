package com.kt.social.auth.controller;

import com.kt.social.auth.dto.LoginRequest;
import com.kt.social.auth.dto.RegisterRequest;
import com.kt.social.auth.dto.TokenResponse;
import com.kt.social.auth.model.RefreshToken;
import com.kt.social.auth.service.AuthService;
import com.kt.social.auth.service.RefreshTokenService;
import com.kt.social.auth.security.JwtProvider;
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
    private final JwtProvider jwtProvider;

    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");

        RefreshToken token = refreshTokenService.findByToken(refreshToken)
                .map(refreshTokenService::verifyExpiration)
                .orElseThrow(() -> new RuntimeException("Invalid refresh token. Please log in again."));

        var user = token.getUser();
        var userDetails = org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword())
                .roles(user.getRole())
                .build();

        String newAccessToken = jwtProvider.generateToken(userDetails);

        return ResponseEntity.ok(new TokenResponse(newAccessToken, token.getToken()));
    }
}