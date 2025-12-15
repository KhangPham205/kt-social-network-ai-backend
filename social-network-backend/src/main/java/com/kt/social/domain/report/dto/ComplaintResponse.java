package com.kt.social.domain.report.dto;

import com.kt.social.domain.react.enums.TargetType;
import com.kt.social.domain.report.enums.ComplaintStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplaintResponse {
    private Long id;
    private Long targetId;
    private TargetType targetType;
    private Long userId;
    private String userDisplayName;
    private String content;       // Nội dung khiếu nại
    private ComplaintStatus status;
    private Instant createdAt;
    private Instant updatedAt;
}