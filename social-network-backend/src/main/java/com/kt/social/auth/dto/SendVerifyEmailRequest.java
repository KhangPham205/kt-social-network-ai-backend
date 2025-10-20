package com.kt.social.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SendVerifyEmailRequest {
    @NotBlank
    @Email
    private String email;
}
