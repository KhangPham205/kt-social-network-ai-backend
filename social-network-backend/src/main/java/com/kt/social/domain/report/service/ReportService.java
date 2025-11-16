package com.kt.social.domain.report.service;

import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.report.dto.ReportDto;
import com.kt.social.domain.report.dto.ReportRequestDto;
import com.kt.social.domain.report.dto.ReportReviewDto;
import com.kt.social.domain.report.enums.ReportStatus;
import org.springframework.data.domain.Pageable;

public interface ReportService {
    // (Cho User)
    ReportDto createReport(Long reporterId, ReportRequestDto request);

    // (Cho Moderator)
    PageVO<ReportDto> getReports(String filter, ReportStatus status, Pageable pageable);

    ReportDto reviewReport(Long reportId, ReportReviewDto request, Long reviewerId);
}
