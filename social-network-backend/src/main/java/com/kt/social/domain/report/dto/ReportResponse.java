package com.kt.social.domain.report.dto;

import com.kt.social.domain.react.enums.TargetType;
import com.kt.social.domain.report.enums.ReportReason;
import com.kt.social.domain.report.enums.ReportStatus;
import com.kt.social.domain.report.model.Report;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class ReportResponse {
    private Long id;
    private TargetType targetType;
    private String targetId;

    private Long reporterId;
    private String reporterName;
    private String reporterAvatar;

    private ReportReason reason;
    private String customReason;
    private Boolean isBannedBySystem;
    private Instant createdAt;
}