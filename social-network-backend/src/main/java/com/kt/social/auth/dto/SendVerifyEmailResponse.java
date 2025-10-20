package com.kt.social.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SendVerifyEmailResponse {
    private String message;
    private String code;
}
