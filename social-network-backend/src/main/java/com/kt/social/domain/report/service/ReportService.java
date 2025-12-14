package com.kt.social.domain.report.service;

import com.kt.social.common.vo.PageVO;
import com.kt.social.domain.report.dto.*;
import org.springframework.data.domain.Pageable;

public interface ReportService {
    // Report
    ReportResponse createReport(Long reporterId, CreateReportRequest request);
    ReportResponse processReport(Long reportId, ProcessReportRequest request);
    ReportResponse getReportById(Long reportId);
    PageVO<ReportResponse> getReports(String filter, Pageable pageable);
    PageVO<ComplaintResponse> getComplaints(String filter, Pageable pageable);

    // Complaint
    ComplaintResponse createComplaint(CreateComplaintRequest request);
    ComplaintResponse resolveComplaint(Long complaintId, ResolveComplaintRequest request);

}
