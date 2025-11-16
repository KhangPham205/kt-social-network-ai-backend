package com.kt.social.domain.report.dto;

import com.kt.social.domain.report.enums.ReportStatus;
import lombok.Data;

@Data
public class ReportReviewDto {
    private ReportStatus status; // (APPROVED hoặc REJECTED)
    private String moderatorNotes;
}