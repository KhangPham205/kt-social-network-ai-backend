package com.kt.social.domain.report.dto;

import com.kt.social.domain.report.enums.ReportStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProcessReportRequest {
    private ReportStatus status;
    private String note;
}