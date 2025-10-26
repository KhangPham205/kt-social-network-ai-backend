package com.kt.social.auth.service.impl;

import com.kt.social.auth.dto.*;
import com.kt.social.auth.enums.AccountStatus;
import com.kt.social.auth.model.PasswordResetToken;
import com.kt.social.auth.model.RefreshToken;
import com.kt.social.auth.model.Role;
import com.kt.social.auth.model.UserCredential;
import com.kt.social.auth.repository.PasswordResetTokenRepository;
import com.kt.social.auth.repository.RoleRepository;
import com.kt.social.auth.repository.UserCredentialRepository;
import com.kt.social.auth.security.JwtProvider;
import com.kt.social.auth.service.AuthService;
import com.kt.social.auth.service.RefreshTokenService;
import com.kt.social.domain.user.model.UserInfo;
import com.kt.social.domain.user.repository.UserInfoRepository;
import com.kt.social.domain.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final RoleRepository roleRepository;
    private final UserCredentialRepository userCredentialRepository;
    private final JwtProvider jwtProvider;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;
    private final UserInfoRepository userInfoRepository;

    @Override
    public RegisterResponse register(RegisterRequest registerRequest) {
        if (userCredentialRepository.existsByUsername(registerRequest.getUsername())) {
            throw new IllegalArgumentException("Username is already in use");
        }

        Role userRole = roleRepository.findByName("USER")
                .orElseThrow(() -> new IllegalStateException("Role not found"));

        UserCredential userCredential = UserCredential.builder()
                .username(registerRequest.getUsername())
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .email(registerRequest.getEmail())
                .role(userRole)
                .status(AccountStatus.PENDING)
                .build();

        userCredentialRepository.save(userCredential);

        com.kt.social.domain.user.model.User user = com.kt.social.domain.user.model.User .builder()
                .displayName(registerRequest.getFullname())
                .isActive(true)
                .credential(userCredential)
                .build();

        userRepository.save(user);

        UserInfo userInfo = UserInfo.builder()
                .bio("")
                .favorites("")
                .dateOfBirth(registerRequest.getDateOfBirth())
                .user(user)
                .build();

        userInfoRepository.save(userInfo);

        UserDetails userDetails = buildUserDetails(userCredential);
        String accessToken = jwtProvider.generateToken(userDetails);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(userCredential);

        return new RegisterResponse("Registered Successfully");
    }

    @Override
    public LoginResponse login(LoginRequest loginRequest) {
        UserCredential userCredential = userCredentialRepository.findByUsername(loginRequest.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + loginRequest.getUsername()));

        if (!passwordEncoder.matches(loginRequest.getPassword(), userCredential.getPassword())) {
            throw new IllegalArgumentException("Wrong password");
        }

        if (userCredential.getStatus() != AccountStatus.ACTIVE) {
            return LoginResponse.builder()
                    .email(userCredential.getEmail())
                    .status(userCredential.getStatus())
                    .build();
        }

        UserDetails userDetails = buildUserDetails(userCredential);
        String accessToken = jwtProvider.generateToken(userDetails);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(userCredential);

        return LoginResponse.builder()
                .email(userCredential.getEmail())
                .status(userCredential.getStatus())
                .token(TokenResponse.builder()
                        .accessToken(accessToken)
                        .refreshToken(refreshToken.getToken())
                        .build())
                .build();
    }

    @Override
    public SendVerifyEmailResponse sendVerificationCode(String email) {
        UserCredential user = userCredentialRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Email not found"));

        String code = String.format("%06d", new SecureRandom().nextInt(1_000_000));
        user.setVerificationCode(code);
        user.setStatus(AccountStatus.PENDING);
        userCredentialRepository.save(user);

        log.info("Verification code for {}: {}", email, code);

        return new SendVerifyEmailResponse("Verification code generated successfully", code);
    }

    @Override
    @Transactional
    public boolean verifyOtp(OtpVerificationRequest request) {
        UserCredential user = userCredentialRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Email not found"));

        return switch (request.getType()) {
            case VERIFY_EMAIL -> handleEmailVerification(user, request.getCode());
            case RESET_PASSWORD -> handlePasswordReset(user, request.getCode());
            default -> throw new IllegalArgumentException("Invalid verification type");
        };
    }

    // --------------------- Helper methods --------------------------
    private UserDetails buildUserDetails(UserCredential userCredential) {
        return User.withUsername(userCredential.getUsername())
                .password(userCredential.getPassword())
                .roles(userCredential.getRole().getName())
                .build();
    }

    private boolean handleEmailVerification(UserCredential user, String code) {
        if (code.equals(user.getVerificationCode())) {
            user.setStatus(AccountStatus.ACTIVE);
            user.setVerificationCode(null);
            userCredentialRepository.save(user);
            return true;
        }
        return false;
    }

    private boolean handlePasswordReset(UserCredential user, String code) {
        PasswordResetToken token = passwordResetTokenRepository.findByEmailAndCode(user.getEmail(), code)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired code"));

        if (token.getExpiryDate().isBefore(Instant.now())) {
            throw new IllegalStateException("Code expired");
        }

        user.setPassword(token.getNewPassword());
        passwordResetTokenRepository.delete(token);
        userCredentialRepository.save(user);
        return true;
    }
}