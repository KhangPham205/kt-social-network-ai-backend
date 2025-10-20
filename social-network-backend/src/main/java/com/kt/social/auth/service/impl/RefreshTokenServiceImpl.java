package com.kt.social.auth.service.impl;

import com.kt.social.auth.dto.RefreshTokenRequest;
import com.kt.social.auth.dto.LoginResponse;
import com.kt.social.auth.dto.TokenResponse;
import com.kt.social.auth.model.RefreshToken;
import com.kt.social.auth.model.UserCredential;
import com.kt.social.auth.repository.RefreshTokenRepository;
import com.kt.social.auth.security.JwtProvider;
import com.kt.social.auth.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh.expiration}")
    private Long refreshExpiration;

    @Override
    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    @Override
    public TokenResponse refresh(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        RefreshToken token = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new RuntimeException("Invalid refresh token. Please log in again."));

        if (token.getExpiryDate().isBefore(java.time.Instant.now())) {
            refreshTokenRepository.delete(token);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired code");
        }

        var user = token.getUser();

        UserDetails userDetails = User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .roles(user.getRole().getName())
                .build();

        String newAccessToken = jwtProvider.generateToken(userDetails);
        return TokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)
                .build();
    }

    @Override
    public RefreshToken createRefreshToken(UserCredential user) {
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiryDate(Instant.now().plusSeconds(refreshExpiration))
                .build();
        return refreshTokenRepository.save(refreshToken);
    }
}
