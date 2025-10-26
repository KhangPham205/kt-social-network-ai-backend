package com.kt.social.domain.user.repository;

import com.kt.social.domain.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByCredentialUsername(String username);
}
