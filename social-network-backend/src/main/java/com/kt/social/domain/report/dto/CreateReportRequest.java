package com.kt.social.domain.report.dto;

import com.kt.social.domain.react.enums.TargetType;
import com.kt.social.domain.report.enums.ReportReason;
import lombok.Data;

@Data
public class CreateReportRequest {
    private TargetType targetType; // POST, COMMENT
    private String targetId;
    private ReportReason reason;
    private String customReason;   // Nếu reason là OTHER
}