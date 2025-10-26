package com.kt.social.auth.util;

import com.kt.social.auth.model.UserCredential;
import com.kt.social.auth.repository.UserCredentialRepository;
import com.kt.social.domain.user.model.User;
import com.kt.social.domain.user.repository.UserRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static Optional<String> getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return Optional.ofNullable(auth.getName());
    }

    public static Optional<UserCredential> getCurrentUserCredential(UserCredentialRepository repo) {
        return getCurrentUsername().flatMap(repo::findByUsername);
    }

    public Long getCurrentUserId(UserCredentialRepository repo) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new RuntimeException("User not authenticated");
        }

        String username = auth.getName();
        return repo.findByUsername(username)
                .map(UserCredential::getId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public static User getCurrentUser(
            UserCredentialRepository credRepo,
            UserRepository userRepo
    ) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        UserCredential cred = credRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Credential not found"));

        return userRepo.findByCredential(cred);
    }
}