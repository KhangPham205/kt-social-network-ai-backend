package com.kt.social.domain.report.dto;

import com.kt.social.domain.report.enums.ReportStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdateReportRequest {
    private List<Long> reportIds;
    private ReportStatus reportStatus;
}
