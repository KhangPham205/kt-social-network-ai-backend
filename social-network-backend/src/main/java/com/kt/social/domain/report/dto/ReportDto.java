package com.kt.social.domain.report.dto;

import com.kt.social.domain.report.enums.ReportReason;
import com.kt.social.domain.report.enums.ReportStatus;
import com.kt.social.domain.report.enums.ReportableType;
import lombok.Data;
import java.time.Instant;

@Data
public class ReportDto {
    private Long id;

    // Người báo cáo
    private Long reporterId;
    private String reporterName;

    // Nội dung bị báo cáo
    private ReportableType targetType;
    private Long targetId;

    // Thông tin báo cáo
    private ReportReason reason;
    private String customReason;
    private Instant createdAt;

    // Thông tin duyệt
    private ReportStatus status;
    private Long reviewerId; // ID của Mod
    private String moderatorNotes;
    private Instant reviewedAt;
}