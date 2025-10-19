package com.kt.social.auth.dto;

import lombok.Data;

@Data
public class VerifyResetCodeRequest {
    private String email;
    private String code;
}
