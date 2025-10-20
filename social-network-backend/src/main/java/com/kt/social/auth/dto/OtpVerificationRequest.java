package com.kt.social.auth.dto;

<<<<<<< Updated upstream:social-network-backend/src/main/java/com/kt/social/auth/dto/VerifyResetCodeRequest.java
import lombok.Data;

@Data
public class VerifyResetCodeRequest {
=======
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
>>>>>>> Stashed changes:social-network-backend/src/main/java/com/kt/social/auth/dto/OtpVerificationRequest.java
    private String email;
    private String code;

    @NotBlank
    @Enumerated(EnumType.STRING)
    private OtpType type;
}
