package com.kt.social.auth.repository;

import com.kt.social.auth.model.UserCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserCredentialRepository extends JpaRepository<UserCredential, Long> {

    Optional<UserCredential> findByUsername(String username);
    boolean existsByUsername(String username);
}
