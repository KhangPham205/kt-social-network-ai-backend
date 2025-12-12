package com.kt.social.domain.admin.dto;

import lombok.Data;

@Data
public class ChangeStatusRequest {
    private Long id;
    private String reason; // Lý do khóa/mở khóa (Optional)
}