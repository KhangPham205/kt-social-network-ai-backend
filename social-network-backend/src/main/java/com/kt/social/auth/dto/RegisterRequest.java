package com.kt.social.auth.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class RegisterRequest {
    private String fullname;
    private String username;
    private String password;
    private String email;
    private Instant dateOfBirth;
}