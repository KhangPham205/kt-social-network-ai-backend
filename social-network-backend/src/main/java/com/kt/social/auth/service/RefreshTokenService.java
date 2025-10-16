package com.kt.social.auth.service;

import com.kt.social.auth.model.RefreshToken;
import com.kt.social.auth.model.UserCredential;

import java.util.Optional;

public interface RefreshTokenService {
    Optional<RefreshToken> findByToken(String token);

    RefreshToken createRefreshToken(UserCredential user);
    RefreshToken verifyExpiration(RefreshToken token);
    void deleteByUser(UserCredential user);
}