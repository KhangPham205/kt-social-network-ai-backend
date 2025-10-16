package com.kt.social.auth.service.impl;

import com.kt.social.auth.dto.LoginRequest;
import com.kt.social.auth.dto.RegisterRequest;
import com.kt.social.auth.dto.TokenResponse;
import com.kt.social.auth.model.RefreshToken;
import com.kt.social.auth.model.Role;
import com.kt.social.auth.model.UserCredential;
import com.kt.social.auth.repository.RoleRepository;
import com.kt.social.auth.repository.UserCredentialRepository;
import com.kt.social.auth.security.JwtProvider;
import com.kt.social.auth.service.AuthService;
import com.kt.social.auth.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final RoleRepository roleRepository;
    private final UserCredentialRepository repository;
    private final JwtProvider jwtProvider;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    @Override
    public TokenResponse register(RegisterRequest registerRequest) {
        if (repository.existsByUsername(registerRequest.getUsername())) {
            throw new RuntimeException("Username is already in use");
        }

        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new RuntimeException("Role not found"));

        UserCredential userCredential = UserCredential.builder()
                .username(registerRequest.getUsername())
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .roles(Set.of(userRole))
                .enabled(true)
                .build();

        repository.save(userCredential);

        UserDetails userDetails = User
                .withUsername(userCredential.getUsername())
                .password(userCredential.getPassword())
                .roles(userCredential.getRole())
                .build();

        String accessToken = jwtProvider.generateToken(userDetails);

        RefreshToken refreshToken = refreshTokenService.createRefreshToken(userCredential);

        return new TokenResponse(accessToken, refreshToken.getToken());
    }

    @Override
    public TokenResponse login(LoginRequest loginRequest) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword())
        );

        UserCredential userCredential = repository.findByUsername(loginRequest.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + loginRequest.getUsername()));

        if (!userCredential.isEnabled()) {
            throw new RuntimeException("User is disabled");
        }

        UserDetails userDetails = User
                .withUsername(userCredential.getUsername())
                .password(userCredential.getPassword())
                .roles(userCredential.getRole())
                .build();

        String accessToken = jwtProvider.generateToken(userDetails);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(userCredential);

        return new TokenResponse(accessToken, refreshToken.getToken());
    }
}
