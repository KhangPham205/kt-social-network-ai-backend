package com.kt.social.auth.service.impl;

import com.kt.social.auth.dto.LoginRequest;
import com.kt.social.auth.dto.RegisterRequest;
import com.kt.social.auth.dto.TokenResponse;
import com.kt.social.auth.enums.AccountStatus;
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

import java.util.Optional;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final RoleRepository roleRepository;
    private final UserCredentialRepository userCredentialRepository;
    private final JwtProvider jwtProvider;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    @Override
    public String register(RegisterRequest registerRequest) {
        if (userCredentialRepository.existsByUsername(registerRequest.getUsername())) {
            throw new RuntimeException("Username is already in use");
        }

        Role userRole = roleRepository.findByName("USER")
                .orElseThrow(() -> new RuntimeException("Role not found"));

        UserCredential userCredential = UserCredential.builder()
                .username(registerRequest.getUsername())
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .email(registerRequest.getEmail())
                .role(userRole)
                .build();

        userCredentialRepository.save(userCredential);

        UserDetails userDetails = User
                .withUsername(userCredential.getUsername())
                .password(userCredential.getPassword())
                .roles(userRole.getName())
                .build();

        String accessToken = jwtProvider.generateToken(userDetails);

        RefreshToken refreshToken = refreshTokenService.createRefreshToken(userCredential);

        return "Registered Successfully";
    }

    @Override
    public TokenResponse login(LoginRequest loginRequest) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword())
        );

        UserCredential userCredential = userCredentialRepository.findByUsername(loginRequest.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + loginRequest.getUsername()));

        if (!(userCredential.getStatus() != null && userCredential.getStatus().equals(AccountStatus.ACTIVE))) {
            throw new RuntimeException("User is disabled");
        }

        UserDetails userDetails = User
                .withUsername(userCredential.getUsername())
                .password(userCredential.getPassword())
                .roles(userCredential.getRole().getName())
                .build();

        String accessToken = jwtProvider.generateToken(userDetails);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(userCredential);

        return new TokenResponse(accessToken, refreshToken.getToken());
    }

    @Override
    public String sendVerificationCode(String email) {
        Optional<UserCredential> optionalUser = userCredentialRepository.findByEmail(email);
        if (optionalUser.isEmpty()) {
            throw new RuntimeException("Email not found");
        }

        UserCredential user = optionalUser.get();
        String code = String.format("%06d", new Random().nextInt(999999));
        user.setVerificationCode(code);
        user.setStatus(AccountStatus.PENDING);
        userCredentialRepository.save(user);

        // Tạm thời chỉ log ra hoặc trả về
        System.out.println("Verification code for " + email + ": " + code);
        return code;
    }

    @Override
    public boolean verifyEmail(String email, String code) {
        UserCredential user = userCredentialRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Email not found"));

        if (user.getVerificationCode() != null && user.getVerificationCode().equals(code)) {
            user.setStatus(AccountStatus.ACTIVE);
            user.setVerificationCode(null);
            userCredentialRepository.save(user);
            return true;
        }

        return false;
    }
}
