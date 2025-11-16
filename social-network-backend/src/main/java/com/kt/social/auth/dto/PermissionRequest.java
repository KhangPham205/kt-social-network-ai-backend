package com.kt.social.auth.dto;

import lombok.Data;

@Data
public class PermissionRequest {
    private String resource;
    private String action;
    private String description;
}
