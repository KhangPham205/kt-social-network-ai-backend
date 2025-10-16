package com.kt.social.auth.repository;

import com.kt.social.auth.model.RefreshToken;
import com.kt.social.auth.model.UserCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
    void deleteByUser(UserCredential user);
}