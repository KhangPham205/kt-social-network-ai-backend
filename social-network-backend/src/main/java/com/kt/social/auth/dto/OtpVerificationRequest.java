package com.kt.social.auth.dto;

import com.kt.social.auth.enums.OtpType;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import lombok.Data;

@Data
public class OtpVerificationRequest {
    @NotBlank
    @Email
    private String email;
    private String code;

    @NotBlank
    @Enumerated(EnumType.STRING)
    private OtpType type;
}
