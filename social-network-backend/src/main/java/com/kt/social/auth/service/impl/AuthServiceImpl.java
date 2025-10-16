package com.kt.social.auth.service.impl;

import com.kt.social.auth.dto.LoginRequest;
import com.kt.social.auth.dto.RegisterRequest;
import com.kt.social.auth.dto.TokenResponse;
import com.kt.social.auth.model.UserCredential;
import com.kt.social.auth.repository.UserCredentialRepository;
import com.kt.social.auth.security.JwtProvider;
import com.kt.social.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserCredentialRepository repository;
    private final JwtProvider jwtProvider;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;

    @Override
    public TokenResponse register(RegisterRequest registerRequest) {
        if (repository.existsByUsername(registerRequest.getUsername())) {
            throw new RuntimeException("Username is already in use");
        }

        UserCredential userCredential = UserCredential.builder()
                .username(registerRequest.getUsername())
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .role("USER")
                .build();

        repository.save(userCredential);

        String token = jwtProvider.generateToken(new User(
                userCredential.getUsername(), userCredential.getPassword(), Collections.emptyList()
        ));

        return new TokenResponse(token);
    }

    @Override
    public TokenResponse login(LoginRequest loginRequest) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword())
        );

        UserCredential userCredential = repository.findByUsername(loginRequest.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + loginRequest.getUsername()));

        String token = jwtProvider.generateToken(
                new User(loginRequest.getUsername(), loginRequest.getPassword(), Collections.emptyList())
        );

        return new TokenResponse(token);
    }
}
