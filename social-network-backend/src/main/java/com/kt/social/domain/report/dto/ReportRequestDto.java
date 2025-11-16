package com.kt.social.domain.report.dto;

import com.kt.social.domain.report.enums.ReportReason;
import com.kt.social.domain.report.enums.ReportableType;
import lombok.Data;

@Data
public class ReportRequestDto {
    private ReportableType targetType;
    private Long targetId;
    private ReportReason reason;
    private String customReason; // (Chỉ dùng khi reason = OTHER)
}